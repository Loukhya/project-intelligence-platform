package com.dpi.platform.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.dpi.platform.event.JavaParsedEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
@Transactional
public class JavaParserServiceImpl implements JavaParserService {

    private static final Logger log = LoggerFactory.getLogger(JavaParserServiceImpl.class);

    private static final Pattern REQ_PATTERN = Pattern.compile("@Requirement\\s*\\(\\s*id\\s*=\\s*\"([^\"]+)\"\\s*\\)|@Requirement\\s*\\(\\s*\"([^\"]+)\"\\s*\\)|@Requirement\\s+([a-fA-F0-9\\-]+)");
    private static final Pattern TASK_PATTERN = Pattern.compile("@Task\\s*\\(\\s*id\\s*=\\s*\"([^\"]+)\"\\s*\\)|@Task\\s*\\(\\s*\"([^\"]+)\"\\s*\\)|@Task\\s+([a-fA-F0-9\\-]+)");

    private static final int MAX_SIGNATURE_LENGTH = 512;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JavaClassRepository javaClassRepository;

    @Autowired
    private JavaMethodRepository javaMethodRepository;

    @Autowired
    private MethodCallRepository methodCallRepository;

    @Autowired
    private CodeMappingRepository codeMappingRepository;

    @Autowired
    private GitService gitService;

    private JavaParser javaParser;

    @PostConstruct
    public void configureParser() {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.javaParser = new JavaParser(config);
        // Also configure StaticJavaParser just in case
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    /**
     * Scans the project directory for Java files and builds class/method inventory.
     * @Requirement(id="33333333-aaaa-1111-bbbb-cccccccccccc")
     */
    @Override
    public void scanProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        String localPath = project.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Local path is not configured for project: " + project.getName());
        }

        // Pull latest if configured
        if (project.getRepositoryUrl() != null && !project.getRepositoryUrl().trim().isEmpty()) {
            try {
                gitService.pullLatest(localPath);
            } catch (Exception e) {
                log.warn("Failed to pull repository before parsing: {}. Scanning local files anyway.", e.getMessage());
            }
        }

        File projectDir = new File(localPath);
        if (!projectDir.exists()) {
            throw new IllegalArgumentException("Physical project directory does not exist: " + localPath);
        }

        log.info("Starting static analysis for project {} in {}", project.getName(), localPath);

        // Delete old mappings/classes/methods for this project to maintain fresh state
        clearOldProjectScanData(projectId);

        List<File> javaFiles = findJavaFiles(projectDir);
        log.info("Found {} Java files to parse.", javaFiles.size());

        Map<String, JavaClass> classMap = new HashMap<>();
        Map<String, JavaMethod> methodMap = new HashMap<>();
        Map<MethodDeclaration, JavaMethod> astToEntityMap = new HashMap<>();

        // First Pass: Extract Classes and Methods
        for (File file : javaFiles) {
            try {
                parseFileClassesAndMethods(file, project, classMap, methodMap, astToEntityMap);
            } catch (Exception e) {
                log.error("Failed to parse file: {} - {}", file.getAbsolutePath(), e.getMessage());
            }
        }

        // Second Pass: Parse Method Calls (Call Graph assembly)
        for (Map.Entry<MethodDeclaration, JavaMethod> entry : astToEntityMap.entrySet()) {
            MethodDeclaration astMethod = entry.getKey();
            JavaMethod callerEntity = entry.getValue();
            try {
                parseMethodCalls(astMethod, callerEntity, methodMap);
            } catch (Exception e) {
                log.warn("Failed to parse calls for method {}: {}", callerEntity.getSignature(), e.getMessage());
            }
        }

        log.info("Analysis complete! Saved {} classes, {} methods, and resolved call graph.", 
                classMap.size(), methodMap.size());
        eventPublisher.publishEvent(new JavaParsedEvent(this, projectId, classMap.size(), methodMap.size()));
    }

    @Override
    public List<CodeMapping> getProjectMappings(UUID projectId) {
        return codeMappingRepository.findByJavaClassProjectId(projectId);
    }

    @Override
    public List<CodeMapping> getCodeArea(UUID requirementId, int maxDepth) {
        Requirement requirement = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new IllegalArgumentException("Requirement not found: " + requirementId));

        List<CodeMapping> directMappings = codeMappingRepository.findByRequirementId(requirementId);
        List<CodeMapping> codeArea = new ArrayList<>(directMappings);

        Set<UUID> visitedMethodIds = directMappings.stream()
                .map(m -> m.getJavaMethod() != null ? m.getJavaMethod().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<JavaMethod> currentQueue = directMappings.stream()
                .map(CodeMapping::getJavaMethod)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        double decay = 0.8;
        double currentConfidence = 0.9;

        // Traverse Call Graph to build the context area
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (currentQueue.isEmpty()) {
                break;
            }
            List<JavaMethod> nextQueue = new ArrayList<>();
            currentConfidence *= decay;

            for (JavaMethod method : currentQueue) {
                // Find all methods this method calls
                List<MethodCall> calls = methodCallRepository.findByCallerMethodId(method.getId());
                for (MethodCall call : calls) {
                    JavaMethod callee = call.getCalleeMethod();
                    if (callee != null && !visitedMethodIds.contains(callee.getId())) {
                        visitedMethodIds.add(callee.getId());
                        nextQueue.add(callee);

                        // Synthesize a transitive code mapping representing call dependency
                        CodeMapping transitiveMapping = CodeMapping.builder()
                                .javaMethod(callee)
                                .javaClass(callee.getJavaClass())
                                .requirement(requirement)
                                .confidenceScore(currentConfidence)
                                .mappingSource("CALL_GRAPH")
                                .build();
                        codeArea.add(transitiveMapping);
                    }
                }

                // Also find methods that call this method (incoming dependencies are also context!)
                List<MethodCall> callers = methodCallRepository.findByCalleeMethodId(method.getId());
                for (MethodCall call : callers) {
                    JavaMethod caller = call.getCallerMethod();
                    if (caller != null && !visitedMethodIds.contains(caller.getId())) {
                        visitedMethodIds.add(caller.getId());
                        nextQueue.add(caller);

                        CodeMapping transitiveMapping = CodeMapping.builder()
                                .javaMethod(caller)
                                .javaClass(caller.getJavaClass())
                                .requirement(requirement)
                                .confidenceScore(currentConfidence * 0.7) // slightly lower weight for callers
                                .mappingSource("CALL_GRAPH")
                                .build();
                        codeArea.add(transitiveMapping);
                    }
                }
            }
            currentQueue = nextQueue;
        }

        return codeArea;
    }

    private void clearOldProjectScanData(UUID projectId) {
        List<JavaClass> classes = javaClassRepository.findByProjectId(projectId);
        for (JavaClass cls : classes) {
            javaClassRepository.delete(cls); // Cascade handles methods, calls, and mappings
        }
        javaClassRepository.flush();
    }

    private List<File> findJavaFiles(File dir) {
        try (Stream<Path> walk = Files.walk(Paths.get(dir.getAbsolutePath()))) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to traverse files in directory: {}", dir.getAbsolutePath(), e);
            return Collections.emptyList();
        }
    }

    private void parseFileClassesAndMethods(File file, Project project, 
                                            Map<String, JavaClass> classMap, 
                                            Map<String, JavaMethod> methodMap,
                                            Map<MethodDeclaration, JavaMethod> astToEntityMap) throws IOException {
        ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
        if (!parseResult.isSuccessful()) {
            String problems = parseResult.getProblems().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            throw new IOException("Parser errors: " + problems);
        }
        CompilationUnit cu = parseResult.getResult()
                .orElseThrow(() -> new IOException("Empty AST result"));
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Find all classes in the file
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration classDecl : classes) {
            String className = classDecl.getNameAsString();
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // Save class entity
            JavaClass javaClass = JavaClass.builder()
                    .project(project)
                    .name(className)
                    .packageName(packageName)
                    .filePath(getRelativePath(file, project.getLocalPath()))
                    .build();
            javaClass = javaClassRepository.save(javaClass);
            classMap.put(fullyQualifiedName, javaClass);

            // Scan class javadoc for Requirement tags
            scanCommentsForMappings(classDecl.getComment().map(c -> c.getContent()).orElse(""), javaClass, null);

            // Find all methods in the class
            List<MethodDeclaration> methods = classDecl.getMethods();
            for (MethodDeclaration methodDecl : methods) {
                String methodName = methodDecl.getNameAsString();
                String returnType = methodDecl.getType().asString();
                String parameters = methodDecl.getParameters().stream()
                        .map(Parameter::toString)
                        .collect(Collectors.joining(", "));
                String signature = fullyQualifiedName + "." + methodName + "(" + 
                        methodDecl.getParameters().stream()
                                .map(p -> p.getType().asString())
                                .collect(Collectors.joining(",")) + ")";

                // Save method entity
                JavaMethod javaMethod = JavaMethod.builder()
                        .javaClass(javaClass)
                        .name(methodName)
                        .returnType(returnType)
                        .parameters(parameters)
                        .signature(signature)
                        .build();
                javaMethod = javaMethodRepository.save(javaMethod);
                methodMap.put(signature, javaMethod);
                astToEntityMap.put(methodDecl, javaMethod);

                // Scan method comments for Requirement/Task tags
                scanCommentsForMappings(methodDecl.getComment().map(c -> c.getContent()).orElse(""), javaClass, javaMethod);
            }
        }
    }

    /**
     * Resolves method call expressions into call-graph edges.
     * @Requirement(id="33333333-aaaa-2222-bbbb-cccccccccccc")
     */
    @SuppressWarnings("unchecked")
    private void parseMethodCalls(MethodDeclaration astMethod, JavaMethod callerEntity, Map<String, JavaMethod> methodMap) {
        // Only find direct method calls, NOT those nested inside lambdas
        List<MethodCallExpr> calls = astMethod.findAll(MethodCallExpr.class);
        for (MethodCallExpr call : calls) {
            // Skip calls that are inside lambda expressions — they are noise for the call graph
            if (call.findAncestor(LambdaExpr.class).isPresent()) {
                continue;
            }

            String calleeName = call.getNameAsString();
            // Build a concise signature instead of toString() which can include multi-line lambda bodies
            String calleeSignatureStr = calleeName + "(" +
                    call.getArguments().stream()
                            .map(arg -> arg.getClass().getSimpleName())
                            .collect(Collectors.joining(", ")) + ")";

            // Truncate to DB column max length
            if (calleeSignatureStr.length() > MAX_SIGNATURE_LENGTH) {
                calleeSignatureStr = calleeSignatureStr.substring(0, MAX_SIGNATURE_LENGTH - 3) + "...";
            }

            // Resolve callee target
            JavaMethod calleeEntity = resolveCallee(calleeName, callerEntity.getJavaClass().getPackageName(), methodMap);

            MethodCall methodCall = MethodCall.builder()
                    .callerMethod(callerEntity)
                    .calleeMethod(calleeEntity)
                    .calleeSignature(calleeSignatureStr)
                    .build();
            methodCallRepository.save(methodCall);
        }
    }

    private JavaMethod resolveCallee(String calleeName, String callerPackage, Map<String, JavaMethod> methodMap) {
        // Find if a method matches the name in the same package/project
        // For simplicity: Look for any method signature that contains ".calleeName("
        List<JavaMethod> matches = methodMap.entrySet().stream()
                .filter(entry -> entry.getKey().contains("." + calleeName + "("))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            return null; // external library call
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        // If multiple matches, prioritize the one in the same package or class
        for (JavaMethod match : matches) {
            if (match.getJavaClass().getPackageName().equals(callerPackage)) {
                return match;
            }
        }
        return matches.get(0); // fallback to first match
    }

    /**
     * Scans comments for @Requirement and @Task tags to create code-to-requirement mappings.
     * @Requirement(id="33333333-aaaa-3333-bbbb-cccccccccccc")
     */
    private void scanCommentsForMappings(String commentText, JavaClass javaClass, JavaMethod javaMethod) {
        if (commentText == null || commentText.trim().isEmpty()) {
            return;
        }

        // 1. Extract Requirement IDs
        Matcher reqMatcher = REQ_PATTERN.matcher(commentText);
        while (reqMatcher.find()) {
            String reqIdStr = null;
            if (reqMatcher.group(1) != null) reqIdStr = reqMatcher.group(1);
            else if (reqMatcher.group(2) != null) reqIdStr = reqMatcher.group(2);
            else if (reqMatcher.group(3) != null) reqIdStr = reqMatcher.group(3);

            if (reqIdStr != null) {
                try {
                    UUID reqId = UUID.fromString(reqIdStr.trim());
                    requirementRepository.findById(reqId).ifPresent(req -> {
                        // Check if mapping already exists to prevent duplicate rows
                        saveMappingIfNotExists(req, javaClass, javaMethod, commentText);
                    });
                } catch (IllegalArgumentException e) {
                    log.debug("Skipped non-UUID requirement mapping tag: {}", reqIdStr);
                }
            }
        }
    }

    private void saveMappingIfNotExists(Requirement req, JavaClass javaClass, JavaMethod javaMethod, String commentText) {
        // Parse optional Task ID from the same comment block
        Task associatedTask = null;
        Matcher taskMatcher = TASK_PATTERN.matcher(commentText);
        if (taskMatcher.find()) {
            String taskIdStr = null;
            if (taskMatcher.group(1) != null) taskIdStr = taskMatcher.group(1);
            else if (taskMatcher.group(2) != null) taskIdStr = taskMatcher.group(2);
            else if (taskMatcher.group(3) != null) taskIdStr = taskMatcher.group(3);

            if (taskIdStr != null) {
                try {
                    UUID taskId = UUID.fromString(taskIdStr.trim());
                    associatedTask = taskRepository.findById(taskId).orElse(null);
                } catch (IllegalArgumentException e) {
                    log.debug("Skipped non-UUID task mapping tag: {}", taskIdStr);
                }
            }
        }

        // Save
        CodeMapping mapping = CodeMapping.builder()
                .javaClass(javaClass)
                .javaMethod(javaMethod)
                .requirement(req)
                .task(associatedTask)
                .confidenceScore(1.0) // explicit comment tags have 1.0 confidence
                .mappingSource("TAG")
                .build();
        codeMappingRepository.save(mapping);
    }

    private String getRelativePath(File file, String baseDirectory) {
        try {
            String base = new File(baseDirectory).getCanonicalPath();
            String path = file.getCanonicalPath();
            if (path.startsWith(base)) {
                String relative = path.substring(base.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.replace(File.separatorChar, '/');
            }
        } catch (IOException e) {
            log.warn("Failed to get relative path for file {}: {}", file.getAbsolutePath(), e.getMessage());
        }
        return file.getName();
    }

    @Override
    public List<MethodCall> getProjectMethodCalls(UUID projectId) {
        return methodCallRepository.findByProjectId(projectId);
    }
}

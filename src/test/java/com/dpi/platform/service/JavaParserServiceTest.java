package com.dpi.platform.service;

import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("JavaParser Code Scanning and Graph Mapping Integration Tests")
class JavaParserServiceTest {

    @Autowired
    private JavaParserService javaParserService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EpicRepository epicRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private JavaClassRepository javaClassRepository;

    @Autowired
    private JavaMethodRepository javaMethodRepository;

    @Autowired
    private MethodCallRepository methodCallRepository;

    @Autowired
    private CodeMappingRepository codeMappingRepository;

    @MockBean
    private GitService gitService;

    private Path tempSourceDir;
    private Project project;
    private Requirement requirement;
    private UUID generatedReqId;

    @BeforeEach
    void setUp() throws IOException {
        // Create temp folder for fake Java codebase
        tempSourceDir = Files.createTempDirectory("dpip-test-codebase");

        // Seed project, epic, and feature hierarchy in database FIRST
        project = Project.builder()
                .name("Test Project")
                .category("TEST")
                .difficulty(Difficulty.BEGINNER)
                .status(ProjectStatus.PLANNING)
                .startDate(LocalDate.now())
                .localPath(tempSourceDir.toAbsolutePath().toString())
                .build();
        project = projectRepository.save(project);

        Epic epic = Epic.builder()
                .project(project)
                .name("Test Epic")
                .priority(Priority.HIGH)
                .status(EpicStatus.PLANNED)
                .build();
        epic = epicRepository.save(epic);

        Feature feature = Feature.builder()
                .epic(epic)
                .name("Test Feature")
                .priority(Priority.MEDIUM)
                .status(FeatureStatus.PLANNED)
                .build();
        feature = featureRepository.save(feature);

        // Save Requirement first to let Hibernate generate its UUID
        requirement = Requirement.builder()
                .feature(feature)
                .title("Test Requirement")
                .complexity(Complexity.LOW)
                .priority(Priority.MEDIUM)
                .status(RequirementStatus.APPROVED)
                .type(RequirementType.FUNCTIONAL)
                .build();
        requirement = requirementRepository.save(requirement);
        generatedReqId = requirement.getId();

        // Write TempClassA.java dynamically embedding the generated Requirement UUID in its Javadoc tag
        String classAContent = """
                package com.test;
                
                public class TempClassA {
                    /**
                     * @Requirement(id="%s")
                     */
                    public void methodA() {
                        TempClassB b = new TempClassB();
                        b.methodB();
                    }
                }
                """.formatted(generatedReqId.toString());
        Files.writeString(tempSourceDir.resolve("TempClassA.java"), classAContent);

        // Write TempClassB.java
        String classBContent = """
                package com.test;
                
                public class TempClassB {
                    public void methodB() {
                        System.out.println("Hello call graph!");
                    }
                }
                """;
        Files.writeString(tempSourceDir.resolve("TempClassB.java"), classBContent);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean database tables in strict reverse-dependency order to prevent constraint violations
        codeMappingRepository.deleteAll();
        methodCallRepository.deleteAll();
        javaMethodRepository.deleteAll();
        javaClassRepository.deleteAll();
        requirementRepository.deleteAll();
        featureRepository.deleteAll();
        epicRepository.deleteAll();
        projectRepository.deleteAll();

        // Clean temp directories
        if (tempSourceDir != null && Files.exists(tempSourceDir)) {
            try (var stream = Files.walk(tempSourceDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }

    @Test
    @DisplayName("Given local Java files, when scanning project, then extract classes, methods, call edges, and comments")
    void shouldScanAndAssembleCodeMap() {
        // Arrange
        Mockito.doNothing().when(gitService).pullLatest(Mockito.anyString());

        // Act
        javaParserService.scanProject(project.getId());

        // Assert classes scanned
        List<JavaClass> classes = javaClassRepository.findByProjectId(project.getId());
        assertEquals(2, classes.size(), "Should have scanned 2 Java classes");

        JavaClass classA = classes.stream().filter(c -> c.getName().equals("TempClassA")).findFirst().orElseThrow();
        JavaClass classB = classes.stream().filter(c -> c.getName().equals("TempClassB")).findFirst().orElseThrow();
        assertEquals("com.test", classA.getPackageName());
        assertEquals("com.test", classB.getPackageName());

        // Assert methods scanned
        List<JavaMethod> methodsClassA = javaMethodRepository.findByJavaClassId(classA.getId());
        assertEquals(1, methodsClassA.size(), "TempClassA should have 1 method");
        JavaMethod methodA = methodsClassA.get(0);
        assertEquals("methodA", methodA.getName());

        List<JavaMethod> methodsClassB = javaMethodRepository.findByJavaClassId(classB.getId());
        assertEquals(1, methodsClassB.size(), "TempClassB should have 1 method");
        JavaMethod methodB = methodsClassB.get(0);
        assertEquals("methodB", methodB.getName());

        // Assert method call edge resolved
        List<MethodCall> calls = methodCallRepository.findByCallerMethodId(methodA.getId());
        assertFalse(calls.isEmpty(), "methodA should call another method");
        MethodCall call = calls.get(0);
        assertNotNull(call.getCalleeMethod(), "Callee method should be resolved");
        assertEquals(methodB.getId(), call.getCalleeMethod().getId(), "Callee should be methodB");

        // Assert comment mapping generated
        List<CodeMapping> mappings = codeMappingRepository.findByRequirementId(generatedReqId);
        assertEquals(1, mappings.size(), "Should have 1 direct code mapping to the requirement");
        CodeMapping mapping = mappings.get(0);
        assertEquals(classA.getId(), mapping.getJavaClass().getId());
        assertEquals(methodA.getId(), mapping.getJavaMethod().getId());
        assertEquals("TAG", mapping.getMappingSource());
        assertEquals(1.0, mapping.getConfidenceScore());
    }

    @Test
    @DisplayName("Given scanned call-graph, when requesting code area context, then return direct mappings and calling dependencies")
    void shouldRetrieveTransitiveCodeArea() {
        // Arrange
        Mockito.doNothing().when(gitService).pullLatest(Mockito.anyString());
        javaParserService.scanProject(project.getId());

        // Act
        List<CodeMapping> codeArea = javaParserService.getCodeArea(generatedReqId, 2);

        // Assert
        // Direct mapping (TempClassA methodA, confidence = 1.0)
        // Transitive mapping (TempClassB methodB, confidence = 0.9 * decay)
        assertEquals(2, codeArea.size(), "Code area should contain direct + transitive mapped elements");

        CodeMapping direct = codeArea.stream()
                .filter(m -> m.getMappingSource().equals("TAG"))
                .findFirst().orElseThrow();
        assertEquals("methodA", direct.getJavaMethod().getName());
        assertEquals(1.0, direct.getConfidenceScore());

        CodeMapping transitive = codeArea.stream()
                .filter(m -> m.getMappingSource().equals("CALL_GRAPH"))
                .findFirst().orElseThrow();
        assertEquals("methodB", transitive.getJavaMethod().getName());
        assertTrue(transitive.getConfidenceScore() < 1.0, "Transitive mappings should have decayed confidence");
    }
}

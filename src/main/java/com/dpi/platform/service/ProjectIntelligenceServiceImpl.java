package com.dpi.platform.service;

import com.dpi.platform.dto.IntelligenceQueryResponse;
import com.dpi.platform.dto.ProjectFullDTO;
import com.dpi.platform.model.CodeMapping;
import com.dpi.platform.repository.CodeMappingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectIntelligenceServiceImpl implements ProjectIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ProjectIntelligenceServiceImpl.class);

    private final ProjectService projectService;
    private final CodeMappingRepository codeMappingRepository;
    private final GroqClientService groqClientService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectIntelligenceServiceImpl(ProjectService projectService,
                                         CodeMappingRepository codeMappingRepository,
                                         GroqClientService groqClientService,
                                         ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.codeMappingRepository = codeMappingRepository;
        this.groqClientService = groqClientService;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntelligenceQueryResponse queryProject(UUID projectId, String query) {
        log.info("Processing semantic intelligence query for project {}: {}", projectId, query);

        // 1. Load full project hierarchy & mappings
        ProjectFullDTO project = projectService.getProjectFull(projectId);
        List<CodeMapping> mappings = codeMappingRepository.findByJavaClassProjectId(projectId);

        // 2. Token-Saver pre-filtering (extract keywords)
        Set<String> keywords = extractKeywords(query);
        
        // Track which nodes to include in our context
        Set<UUID> epicsToInclude = new HashSet<>();
        Set<UUID> featuresToInclude = new HashSet<>();
        Set<UUID> requirementsToInclude = new HashSet<>();
        Set<UUID> tasksToInclude = new HashSet<>();
        Set<UUID> classesToInclude = new HashSet<>();
        Set<UUID> methodsToInclude = new HashSet<>();

        boolean matchesFound = false;

        // Perform keyword matching across entities
        for (ProjectFullDTO.EpicDTO epic : project.getEpics()) {
            if (containsKeyword(epic.getName(), keywords) || containsKeyword(epic.getDescription(), keywords)) {
                epicsToInclude.add(epic.getId());
                matchesFound = true;
            }
            for (ProjectFullDTO.FeatureDTO feat : epic.getFeatures()) {
                if (containsKeyword(feat.getName(), keywords) || containsKeyword(feat.getDescription(), keywords)) {
                    epicsToInclude.add(epic.getId());
                    featuresToInclude.add(feat.getId());
                    matchesFound = true;
                }
                for (ProjectFullDTO.RequirementDTO req : feat.getRequirements()) {
                    if (containsKeyword(req.getTitle(), keywords) || containsKeyword(req.getDescription(), keywords)) {
                        epicsToInclude.add(epic.getId());
                        featuresToInclude.add(feat.getId());
                        requirementsToInclude.add(req.getId());
                        matchesFound = true;
                    }
                }
                for (ProjectFullDTO.TaskDTO task : feat.getTasks()) {
                    if (containsKeyword(task.getTitle(), keywords) || containsKeyword(task.getDescription(), keywords)) {
                        epicsToInclude.add(epic.getId());
                        featuresToInclude.add(feat.getId());
                        tasksToInclude.add(task.getId());
                        if (task.getRequirementId() != null) {
                            requirementsToInclude.add(task.getRequirementId());
                        }
                        matchesFound = true;
                    }
                }
            }
        }

        // Map classes/methods matched in database codebase mappings
        for (CodeMapping map : mappings) {
            boolean mapMatched = false;
            if (map.getJavaClass() != null && containsKeyword(map.getJavaClass().getName(), keywords)) {
                classesToInclude.add(map.getJavaClass().getId());
                mapMatched = true;
            }
            if (map.getJavaMethod() != null && containsKeyword(map.getJavaMethod().getName(), keywords)) {
                if (map.getJavaClass() != null) classesToInclude.add(map.getJavaClass().getId());
                methodsToInclude.add(map.getJavaMethod().getId());
                mapMatched = true;
            }
            if (mapMatched) {
                matchesFound = true;
                if (map.getRequirement() != null) {
                    requirementsToInclude.add(map.getRequirement().getId());
                    ProjectFullDTO.FeatureDTO f = findFeatureForRequirement(project, map.getRequirement().getId());
                    if (f != null) {
                        featuresToInclude.add(f.getId());
                        UUID epicId = findEpicIdForFeature(project, f.getId());
                        if (epicId != null) epicsToInclude.add(epicId);
                    }
                }
                if (map.getTask() != null) {
                    tasksToInclude.add(map.getTask().getId());
                    if (map.getTask().getRequirementId() != null) {
                        requirementsToInclude.add(map.getTask().getRequirementId());
                    }
                    ProjectFullDTO.FeatureDTO f = findFeatureForTask(project, map.getTask().getId());
                    if (f != null) {
                        featuresToInclude.add(f.getId());
                        UUID epicId = findEpicIdForFeature(project, f.getId());
                        if (epicId != null) epicsToInclude.add(epicId);
                    }
                }
            }
        }

        // If no matching elements found (generic query), include all high-level epics & features for context
        if (!matchesFound) {
            project.getEpics().forEach(e -> {
                epicsToInclude.add(e.getId());
                e.getFeatures().forEach(f -> featuresToInclude.add(f.getId()));
            });
        }

        // 3. Serialize compact Markdown tree and build short-code mappings
        StringBuilder md = new StringBuilder();
        Map<String, String> shortCodeToFullId = new HashMap<>();
        int codeCounter = 1;

        md.append("Project [P1]: ").append(project.getName()).append(" (").append(project.getProgress()).append("% Complete)\n");
        shortCodeToFullId.put("P1", "PROJECT_" + project.getId());

        for (ProjectFullDTO.EpicDTO epic : project.getEpics()) {
            if (!epicsToInclude.contains(epic.getId())) continue;
            
            String eCode = "E" + (codeCounter++);
            shortCodeToFullId.put(eCode, "EPIC_" + epic.getId());
            md.append("  Epic [").append(eCode).append("]: ").append(epic.getName())
              .append(" [Status: ").append(epic.getStatus()).append(", Progress: ").append(epic.getProgress()).append("%]\n");

            for (ProjectFullDTO.FeatureDTO feat : epic.getFeatures()) {
                if (!featuresToInclude.contains(feat.getId())) continue;

                String fCode = "F" + (codeCounter++);
                shortCodeToFullId.put(fCode, "FEATURE_" + feat.getId());
                md.append("    Feature [").append(fCode).append("]: ").append(feat.getName())
                  .append(" [Status: ").append(feat.getStatus()).append(", Progress: ").append(feat.getProgress()).append("%]\n");

                for (ProjectFullDTO.RequirementDTO req : feat.getRequirements()) {
                    if (!requirementsToInclude.contains(req.getId()) && matchesFound) continue;

                    String rCode = "R" + (codeCounter++);
                    shortCodeToFullId.put(rCode, "REQUIREMENT_" + req.getId());
                    md.append("      Requirement [").append(rCode).append("]: ").append(req.getTitle())
                      .append(" [Complexity: ").append(req.getComplexity()).append(", Priority: ").append(req.getPriority()).append("]");
                    if (req.getUserStory() != null) {
                        md.append(" (Story: ").append(req.getUserStory().getStoryText()).append(")");
                    }
                    md.append("\n");

                    // Append mapped methods inside requirement
                    List<CodeMapping> reqMaps = mappings.stream()
                            .filter(m -> m.getRequirement() != null && m.getRequirement().getId().equals(req.getId()))
                            .collect(Collectors.toList());
                    for (CodeMapping m : reqMaps) {
                        if (m.getJavaMethod() != null) {
                            String mCode = "M" + (codeCounter++);
                            shortCodeToFullId.put(mCode, "METHOD_" + m.getJavaMethod().getId());
                            md.append("        Code Mapping [").append(mCode).append("]: ")
                              .append(m.getJavaClass().getName()).append("#").append(m.getJavaMethod().getName()).append("\n");
                        }
                    }
                }

                for (ProjectFullDTO.TaskDTO task : feat.getTasks()) {
                    if (!tasksToInclude.contains(task.getId()) && matchesFound) continue;

                    String tCode = "T" + (codeCounter++);
                    shortCodeToFullId.put(tCode, "TASK_" + task.getId());
                    md.append("      Task [").append(tCode).append("]: ").append(task.getTitle())
                      .append(" [Status: ").append(task.getStatus()).append(", Priority: ").append(task.getPriority()).append("]\n");

                    // Append mapped methods inside task
                    List<CodeMapping> taskMaps = mappings.stream()
                            .filter(m -> m.getTask() != null && m.getTask().getId().equals(task.getId()))
                            .collect(Collectors.toList());
                    for (CodeMapping m : taskMaps) {
                        if (m.getJavaMethod() != null) {
                            String mCode = "M" + (codeCounter++);
                            shortCodeToFullId.put(mCode, "METHOD_" + m.getJavaMethod().getId());
                            md.append("        Code Mapping [").append(mCode).append("]: ")
                              .append(m.getJavaClass().getName()).append("#").append(m.getJavaMethod().getName()).append("\n");
                        }
                    }
                }
            }
        }

        // 4. Prompt construction & Groq invocation
        String systemPrompt = "You are the Project Intelligence Assistant for DPIP.\n" +
                "Your role is to analyze the provided compact project planning and code structure, and answer the developer's question in a helpful, concise manner.\n\n" +
                "Strict Constraints:\n" +
                "1. Keep your answer brief, simple, and direct. Use developer-focused Markdown formatting.\n" +
                "2. Do NOT output any bracketed short codes (like [P1], [E2], [R3], [T4], [M5]) in your visible 'answer' text. Write the actual names of the items naturally (e.g. write 'Factorial Requirement' instead of '[R1]').\n" +
                "3. In the 'references' array, list the corresponding short codes (e.g. 'P1', 'R3', 'M5') for all items you mentioned or referenced in your answer.\n" +
                "4. You must respond ONLY with a JSON object conforming to this exact structure:\n" +
                "{\n" +
                "  \"answer\": \"markdown text answer using actual item names naturally...\",\n" +
                "  \"references\": [\"R1\", \"T2\", \"M3\"]\n" +
                "}\n";

        String userPrompt = "COMPACT PROJECT TREE:\n" + md.toString() + "\n\nUSER QUESTION: " + query;

        try {
            String rawJson = groqClientService.generateCode(systemPrompt, userPrompt);
            log.info("Received Groq response: {}", rawJson);

            // Parse response JSON
            Map<String, Object> parsed = objectMapper.readValue(rawJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String answer = (String) parsed.get("answer");
            if (answer != null) {
                // Strip any residual bracketed codes (e.g. [R1], [T2]) to ensure a clean UI
                answer = answer.replaceAll("\\[[PEFRTM]\\d+\\]", "");
                answer = answer.replaceAll("\\s+", " ").trim();
            }
            @SuppressWarnings("unchecked")
            List<String> rawRefs = (List<String>) parsed.get("references");

            List<String> resolvedRefs = new ArrayList<>();
            if (rawRefs != null) {
                for (String code : rawRefs) {
                    String cleanCode = code.toUpperCase().trim().replace("[", "").replace("]", "");
                    if (shortCodeToFullId.containsKey(cleanCode)) {
                        resolvedRefs.add(shortCodeToFullId.get(cleanCode));
                    }
                }
            }

            return IntelligenceQueryResponse.builder()
                    .answer(answer)
                    .referencedEntityIds(resolvedRefs)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate and parse semantic query response", e);
            return IntelligenceQueryResponse.builder()
                    .answer("I encountered an issue communicating with the AI client. Please verify that your Groq key is active.")
                    .referencedEntityIds(new ArrayList<>())
                    .build();
        }
    }

    private Set<String> extractKeywords(String query) {
        if (query == null) return new HashSet<>();
        String[] words = query.toLowerCase().split("\\W+");
        Set<String> stopWords = new HashSet<>(Arrays.asList("what", "is", "the", "are", "of", "and", "in", "to", "for", "with", "implement", "show", "who", "which", "how", "many", "open", "all", "project", "epic", "feature", "task"));
        Set<String> keywords = new HashSet<>();
        for (String w : words) {
            if (w.length() > 2 && !stopWords.contains(w)) {
                keywords.add(w);
            }
        }
        return keywords;
    }

    private boolean containsKeyword(String field, Set<String> keywords) {
        if (field == null) return false;
        String lowercaseField = field.toLowerCase();
        for (String kw : keywords) {
            if (lowercaseField.contains(kw)) return true;
        }
        return false;
    }

    private ProjectFullDTO.FeatureDTO findFeatureForRequirement(ProjectFullDTO project, UUID reqId) {
        for (ProjectFullDTO.EpicDTO epic : project.getEpics()) {
            for (ProjectFullDTO.FeatureDTO feat : epic.getFeatures()) {
                for (ProjectFullDTO.RequirementDTO req : feat.getRequirements()) {
                    if (req.getId().equals(reqId)) return feat;
                }
            }
        }
        return null;
    }

    private ProjectFullDTO.FeatureDTO findFeatureForTask(ProjectFullDTO project, UUID taskId) {
        for (ProjectFullDTO.EpicDTO epic : project.getEpics()) {
            for (ProjectFullDTO.FeatureDTO feat : epic.getFeatures()) {
                for (ProjectFullDTO.TaskDTO task : feat.getTasks()) {
                    if (task.getId().equals(taskId)) return feat;
                }
            }
        }
        return null;
    }

    private UUID findEpicIdForFeature(ProjectFullDTO project, UUID featId) {
        for (ProjectFullDTO.EpicDTO epic : project.getEpics()) {
            for (ProjectFullDTO.FeatureDTO feat : epic.getFeatures()) {
                if (feat.getId().equals(featId)) return epic.getId();
            }
        }
        return null;
    }
}

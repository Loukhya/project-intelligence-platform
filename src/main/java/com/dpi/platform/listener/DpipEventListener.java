package com.dpi.platform.listener;

import com.dpi.platform.event.CodeAcceptedEvent;
import com.dpi.platform.event.CodeGeneratedEvent;
import com.dpi.platform.event.JavaParsedEvent;
import com.dpi.platform.model.CodeCandidate;
import com.dpi.platform.model.Project;
import com.dpi.platform.model.Task;
import com.dpi.platform.service.AiGatewayService;
import com.dpi.platform.service.JavaParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DpipEventListener {

    private static final Logger log = LoggerFactory.getLogger(DpipEventListener.class);

    @Autowired
    private JavaParserService javaParserService;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Async
    @EventListener
    public void handleCodeGenerated(CodeGeneratedEvent event) {
        CodeCandidate candidate = event.getCandidate();
        log.info("[Event] Code generated for Task ID: {} (Candidate ID: {})", 
                candidate.getTaskId(), candidate.getId());
    }

    @Async
    @EventListener
    public void handleCodeAccepted(CodeAcceptedEvent event) {
        CodeCandidate candidate = event.getCandidate();
        Task task = candidate.getTask();
        Project project = task.getFeature().getEpic().getProject();

        log.info("[Event] Code accepted for Task ID: {}. Triggering async processes...", task.getId());

        // 1. Clean intermediate chat messages to avoid token bloat
        try {
            log.info("Pruning intermediate messages for task ID: {}", task.getId());
            aiGatewayService.cleanIntermediateMessages(task.getId());
        } catch (Exception e) {
            log.warn("Failed to clean intermediate messages: {}", e.getMessage());
        }

        // 2. Trigger automatic codebase analysis scan
        try {
            log.info("Triggering async project scan for project: {}", project.getName());
            javaParserService.scanProject(project.getId());
        } catch (Exception e) {
            log.error("Failed async codebase scan: {}", e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void handleJavaParsed(JavaParsedEvent event) {
        log.info("[Event] Project ID {} rescan completed. Scanned {} classes and {} methods.",
                event.getProjectId(), event.getClassesCount(), event.getMethodsCount());
    }
}

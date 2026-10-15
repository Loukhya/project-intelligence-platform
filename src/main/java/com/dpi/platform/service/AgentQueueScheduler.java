package com.dpi.platform.service;

import com.dpi.platform.repository.AgentSubtaskQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentQueueScheduler.class);

    @Autowired
    private AgentSubtaskQueueRepository agentSubtaskQueueRepository;

    @Scheduled(fixedDelay = 15000) // Poll every 15 seconds
    public void monitorQueue() {
        try {
            long total = agentSubtaskQueueRepository.count();
            long pending = agentSubtaskQueueRepository.findAll().stream()
                    .filter(q -> "PENDING".equalsIgnoreCase(q.getStatus()))
                    .count();
            long claimed = agentSubtaskQueueRepository.findAll().stream()
                    .filter(q -> "CLAIMED".equalsIgnoreCase(q.getStatus()))
                    .count();
            long success = agentSubtaskQueueRepository.findAll().stream()
                    .filter(q -> "SUCCESS".equalsIgnoreCase(q.getStatus()))
                    .count();

            log.info("[DPIP Parent Coordinator] Subtask Queue - Total: {}, PENDING: {}, CLAIMED: {}, SUCCESS: {}", 
                    total, pending, claimed, success);
        } catch (Exception e) {
            log.warn("Failed to query subtask queue metrics: {}", e.getMessage());
        }
    }
}

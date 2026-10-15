package com.dpi.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class GitServiceImpl implements GitService {

    private static final Logger log = LoggerFactory.getLogger(GitServiceImpl.class);

    @Override
    public void cloneRepository(String url, String localPath) {
        if (url == null || url.trim().isEmpty() || localPath == null || localPath.trim().isEmpty()) {
            log.warn("Invalid repository URL or local path. Skipping clone.");
            return;
        }
        File targetDir = new File(localPath);
        if (targetDir.exists() && targetDir.list() != null && targetDir.list().length > 0) {
            log.info("Target directory already exists and is not empty: {}. Skipping clone.", localPath);
            return;
        }
        log.info("Cloning repository {} into {}", url, localPath);
        runCommand(null, "git", "clone", url, localPath);
    }

    @Override
    public void pullLatest(String localPath) {
        if (localPath == null || localPath.trim().isEmpty()) {
            return;
        }
        File targetDir = new File(localPath);
        if (!targetDir.exists() || !new File(targetDir, ".git").exists()) {
            log.warn("Directory {} is not a Git repository. Skipping pull.", localPath);
            return;
        }
        log.info("Pulling latest changes in {}", localPath);
        runCommand(targetDir, "git", "pull");
    }

    @Override
    public void commitAndPush(String localPath, String message) {
        if (localPath == null || localPath.trim().isEmpty()) {
            return;
        }
        File targetDir = new File(localPath);
        if (!targetDir.exists() || !new File(targetDir, ".git").exists()) {
            log.warn("Directory {} is not a Git repository. Skipping commit.", localPath);
            return;
        }
        log.info("Committing and pushing changes in {}", localPath);
        runCommand(targetDir, "git", "add", ".");
        runCommand(targetDir, "git", "commit", "-m", message);
        runCommand(targetDir, "git", "push");
    }

    private void runCommand(File workingDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(workingDir);
            }
            // Discard process stdout/stderr to avoid hanging if buffers fill up, or redirect to logs
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Git command timed out: {}", String.join(" ", command));
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Git command exited with code {}: {}", exitCode, String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to execute Git command: {}", String.join(" ", command), e);
        }
    }
}

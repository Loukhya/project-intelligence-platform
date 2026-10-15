package com.dpi.platform.controller;

import com.dpi.platform.model.Project;
import com.dpi.platform.service.GitHubClientService;
import com.dpi.platform.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GitHubController {

    private final GitHubClientService gitHubClientService;
    private final ProjectService projectService;

    @Autowired
    public GitHubController(GitHubClientService gitHubClientService, ProjectService projectService) {
        this.gitHubClientService = gitHubClientService;
        this.projectService = projectService;
    }

    @GetMapping("/github/status")
    public ResponseEntity<Map<String, Object>> getGitHubStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            String username = gitHubClientService.getAuthenticatedUser();
            if (username != null) {
                status.put("connected", true);
                status.put("username", username);
            } else {
                status.put("connected", false);
                status.put("error", "Invalid or expired token");
            }
        } catch (Exception e) {
            status.put("connected", false);
            status.put("error", e.getMessage());
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/github/repos")
    public ResponseEntity<List<Map<String, Object>>> getGitHubRepos() {
        return ResponseEntity.ok(gitHubClientService.listUserRepositories());
    }

    @PostMapping("/projects/{projectId}/github/connect")
    public ResponseEntity<Project> connectProjectToGitHub(
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> request) {
        
        String repoName = (String) request.get("repoName");
        String owner = (String) request.get("owner");
        Boolean createRepo = (Boolean) request.get("createRepo");

        if (createRepo != null && createRepo) {
            // Create a new repository first
            String description = "Repository for project in DPIP platform";
            Map<String, Object> createdRepo = gitHubClientService.createRepository(repoName, description, true);
            owner = (String) ((Map<?, ?>) createdRepo.get("owner")).get("login");
            repoName = (String) createdRepo.get("name");
        }

        Project updatedProject = projectService.updateProjectGitHub(projectId, owner, repoName);
        return ResponseEntity.ok(updatedProject);
    }

    @GetMapping("/projects/{projectId}/github/commits")
    public ResponseEntity<List<Map<String, Object>>> getProjectCommits(@PathVariable UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (project.getGithubOwner() == null || project.getGithubRepoName() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Map<String, Object>> commits = gitHubClientService.listCommits(project.getGithubOwner(), project.getGithubRepoName());
        return ResponseEntity.ok(commits);
    }

    @GetMapping("/projects/{projectId}/github/branches")
    public ResponseEntity<List<Map<String, Object>>> getProjectBranches(@PathVariable UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (project.getGithubOwner() == null || project.getGithubRepoName() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Map<String, Object>> branches = gitHubClientService.listBranches(project.getGithubOwner(), project.getGithubRepoName());
        return ResponseEntity.ok(branches);
    }

    @PostMapping("/projects/{projectId}/github/branches")
    public ResponseEntity<Map<String, String>> createProjectBranch(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> request) {
        
        Project project = projectService.getProject(projectId);
        if (project.getGithubOwner() == null || project.getGithubRepoName() == null) {
            return ResponseEntity.badRequest().build();
        }

        String branchName = request.get("branchName");
        String fromSha = request.get("fromSha"); // Optional, service will auto-resolve if missing

        gitHubClientService.createBranch(project.getGithubOwner(), project.getGithubRepoName(), branchName, fromSha);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Branch created successfully");
        response.put("branch", branchName);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/projects/{projectId}/github/pulls")
    public ResponseEntity<List<Map<String, Object>>> getProjectPullRequests(@PathVariable UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (project.getGithubOwner() == null || project.getGithubRepoName() == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Map<String, Object>> pulls = gitHubClientService.listPullRequests(project.getGithubOwner(), project.getGithubRepoName());
        return ResponseEntity.ok(pulls);
    }

    @PostMapping("/projects/{projectId}/github/pulls")
    public ResponseEntity<Map<String, Object>> createProjectPullRequest(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> request) {
        
        Project project = projectService.getProject(projectId);
        if (project.getGithubOwner() == null || project.getGithubRepoName() == null) {
            return ResponseEntity.badRequest().build();
        }

        String title = request.get("title");
        String head = request.get("head");
        String base = request.get("base");
        String body = request.get("body");

        Map<String, Object> pr = gitHubClientService.createPullRequest(
                project.getGithubOwner(),
                project.getGithubRepoName(),
                title,
                head,
                base,
                body
        );
        return new ResponseEntity<>(pr, HttpStatus.CREATED);
    }
}

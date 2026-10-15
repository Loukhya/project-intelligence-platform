package com.dpi.platform.service;

import java.util.List;
import java.util.Map;

public interface GitHubClientService {
    String getAuthenticatedUser();
    List<Map<String, Object>> listUserRepositories();
    Map<String, Object> createRepository(String name, String description, boolean isPrivate);
    List<Map<String, Object>> listCommits(String owner, String repo);
    List<Map<String, Object>> listBranches(String owner, String repo);
    void createBranch(String owner, String repo, String branchName, String fromSha);
    List<Map<String, Object>> listPullRequests(String owner, String repo);
    Map<String, Object> createPullRequest(String owner, String repo, String title, String head, String base, String body);
}

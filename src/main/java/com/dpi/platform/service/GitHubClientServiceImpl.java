package com.dpi.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GitHubClientServiceImpl implements GitHubClientService {

    private static final Logger log = LoggerFactory.getLogger(GitHubClientServiceImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${github.pat:}")
    private String pat;

    public GitHubClientServiceImpl(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    private String getActivePat() {
        String activePat = (this.pat != null && !this.pat.trim().isEmpty()) ? this.pat : System.getenv("GITHUB_PAT");
        if (activePat == null || activePat.trim().isEmpty()) {
            throw new IllegalStateException("GitHub Personal Access Token (PAT) is not configured. Please set the GITHUB_PAT environment variable or configured Spring property.");
        }
        return activePat.trim();
    }

    private HttpRequest.Builder newRequestBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + getActivePat())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DPIP-App");
    }

    @Override
    public String getAuthenticatedUser() {
        try {
            HttpRequest request = newRequestBuilder("https://api.github.com/user")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> user = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
                return (String) user.get("login");
            } else {
                log.error("Failed to authenticate with GitHub. Status: {}, Response: {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("Error authenticating with GitHub", e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> listUserRepositories() {
        try {
            HttpRequest request = newRequestBuilder("https://api.github.com/user/repos?type=owner&sort=updated&per_page=100")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                log.error("Failed to list repos. Status: {}, Response: {}", response.statusCode(), response.body());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error listing repositories", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Object> createRepository(String name, String description, boolean isPrivate) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("description", description);
            payload.put("private", isPrivate);
            payload.put("auto_init", true); // Auto initialize with README so it has a main branch and initial commit

            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest request = newRequestBuilder("https://api.github.com/user/repos")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            } else {
                log.error("Failed to create repository. Status: {}, Response: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create repository: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error creating repository", e);
            throw new RuntimeException("Error creating repository", e);
        }
    }

    @Override
    public List<Map<String, Object>> listCommits(String owner, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/commits?per_page=15", owner, repo);
            HttpRequest request = newRequestBuilder(url)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                log.error("Failed to list commits for {}/{}. Status: {}, Response: {}", owner, repo, response.statusCode(), response.body());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error listing commits", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> listBranches(String owner, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            HttpRequest request = newRequestBuilder(url)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                log.error("Failed to list branches. Status: {}, Response: {}", response.statusCode(), response.body());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error listing branches", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void createBranch(String owner, String repo, String branchName, String fromSha) {
        try {
            String activeSha = fromSha;
            if (activeSha == null || activeSha.trim().isEmpty()) {
                // Fetch default branch commit SHA
                String repoUrl = String.format("https://api.github.com/repos/%s/%s", owner, repo);
                HttpRequest repoRequest = newRequestBuilder(repoUrl).GET().build();
                HttpResponse<String> repoResponse = httpClient.send(repoRequest, HttpResponse.BodyHandlers.ofString());
                
                String defaultBranch = "main";
                if (repoResponse.statusCode() == 200) {
                    Map<String, Object> repoInfo = objectMapper.readValue(repoResponse.body(), new TypeReference<Map<String, Object>>() {});
                    if (repoInfo.containsKey("default_branch")) {
                        defaultBranch = (String) repoInfo.get("default_branch");
                    }
                }
                
                String refUrl = String.format("https://api.github.com/repos/%s/%s/git/ref/heads/%s", owner, repo, defaultBranch);
                HttpRequest refRequest = newRequestBuilder(refUrl).GET().build();
                HttpResponse<String> refResponse = httpClient.send(refRequest, HttpResponse.BodyHandlers.ofString());
                
                if (refResponse.statusCode() == 200) {
                    Map<String, Object> refInfo = objectMapper.readValue(refResponse.body(), new TypeReference<Map<String, Object>>() {});
                    @SuppressWarnings("unchecked")
                    Map<String, Object> obj = (Map<String, Object>) refInfo.get("object");
                    activeSha = (String) obj.get("sha");
                } else {
                    throw new RuntimeException("Could not find default branch SHA for refs/heads/" + defaultBranch);
                }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("ref", "refs/heads/" + branchName);
            payload.put("sha", activeSha);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            String url = String.format("https://api.github.com/repos/%s/%s/git/refs", owner, repo);
            HttpRequest request = newRequestBuilder(url)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                log.error("Failed to create branch. Status: {}, Response: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create branch: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error creating branch", e);
            throw new RuntimeException("Error creating branch", e);
        }
    }

    @Override
    public List<Map<String, Object>> listPullRequests(String owner, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/pulls?state=all&per_page=50", owner, repo);
            HttpRequest request = newRequestBuilder(url)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                log.error("Failed to list PRs. Status: {}, Response: {}", response.statusCode(), response.body());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error listing PRs", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Object> createPullRequest(String owner, String repo, String title, String head, String base, String body) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("head", head);
            payload.put("base", base);
            payload.put("body", body);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            String url = String.format("https://api.github.com/repos/%s/%s/pulls", owner, repo);
            HttpRequest request = newRequestBuilder(url)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            } else {
                log.error("Failed to create PR. Status: {}, Response: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create PR: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error creating pull request", e);
            throw new RuntimeException("Error creating pull request", e);
        }
    }
}

package com.dpi.platform.service;

public interface GitService {
    void cloneRepository(String url, String localPath);
    void pullLatest(String localPath);
    void commitAndPush(String localPath, String message);
}

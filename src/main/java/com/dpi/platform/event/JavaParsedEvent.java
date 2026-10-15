package com.dpi.platform.event;

import org.springframework.context.ApplicationEvent;
import java.util.UUID;

public class JavaParsedEvent extends ApplicationEvent {
    private final UUID projectId;
    private final int classesCount;
    private final int methodsCount;

    public JavaParsedEvent(Object source, UUID projectId, int classesCount, int methodsCount) {
        super(source);
        this.projectId = projectId;
        this.classesCount = classesCount;
        this.methodsCount = methodsCount;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getClassesCount() {
        return classesCount;
    }

    public int getMethodsCount() {
        return methodsCount;
    }
}

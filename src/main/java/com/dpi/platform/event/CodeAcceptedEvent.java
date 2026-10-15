package com.dpi.platform.event;

import com.dpi.platform.model.CodeCandidate;
import org.springframework.context.ApplicationEvent;

public class CodeAcceptedEvent extends ApplicationEvent {
    private final CodeCandidate candidate;

    public CodeAcceptedEvent(Object source, CodeCandidate candidate) {
        super(source);
        this.candidate = candidate;
    }

    public CodeCandidate getCandidate() {
        return candidate;
    }
}

package com.stringutil;

import java.util.Objects;

public class StringPadding {

    public String padLeft(String input, int targetLength, char paddingChar) {
        Objects.requireNonNull(input, "String cannot be null");
        if (input.length() >= targetLength) {
            return input;
        }
        int paddingLength = targetLength - input.length();
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < paddingLength; i++) {
            padding.append(paddingChar);
        }
        return padding.toString() + input;
    }

    public String padRight(String input, int targetLength, char paddingChar) {
        Objects.requireNonNull(input, "String cannot be null");
        if (input.length() >= targetLength) {
            return input;
        }
        int paddingLength = targetLength - input.length();
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < paddingLength; i++) {
            padding.append(paddingChar);
        }
        return input + padding.toString();
    }
}
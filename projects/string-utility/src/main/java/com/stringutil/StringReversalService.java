package com.stringutil;

import java.util.Objects;

public class StringReversalService {

    public String reverse(String input) {
        Objects.requireNonNull(input, "Input string cannot be null");
        StringBuilder reversed = new StringBuilder(input).reverse();
        return reversed.toString();
    }
}

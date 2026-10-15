package com.mathutil;

public class FactorialService {

    public long calculateFactorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Input must be a non-negative integer");
        }
        if (n == 0 || n == 1) {
            return 1;
        }
        if (n > 20) {
            throw new IllegalArgumentException("Input must be less than or equal to 20");
        }
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
}
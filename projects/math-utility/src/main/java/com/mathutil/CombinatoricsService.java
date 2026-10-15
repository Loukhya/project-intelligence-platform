package com.mathutil;

import java.util.InputMismatchException;

/**
 * Provides combinatorics calculations such as combinations (nCr) and permutations (nPr).
 * @Requirement(id="e39263f0-34b6-4172-9c06-a1d839f0d223")
 * @Task(id="66f35389-a6d2-46a7-8856-1b1409f0a1e0")
 */
public class CombinatoricsService {

    /**
     * Calculates the factorial of a given number.
     * @param n the input number
     * @return the factorial of n
     */
    private long factorial(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    /**
     * Calculates the number of combinations (nCr) for the given input values.
     * @param n the total number of items
     * @param r the number of items to choose
     * @return the number of combinations
     * @throws InputMismatchException if r is greater than n or if either n or r is negative
     */
    public long nCr(int n, int r) throws InputMismatchException {
        if (r > n || n < 0 || r < 0) {
            throw new InputMismatchException("r cannot be greater than n, and neither n nor r can be negative.");
        }
        return factorial(n) / (factorial(r) * factorial(n - r));
    }

    /**
     * Calculates the number of permutations (nPr) for the given input values.
     * @param n the total number of items
     * @param r the number of items to choose
     * @return the number of permutations
     * @throws InputMismatchException if r is greater than n or if either n or r is negative
     */
    public long nPr(int n, int r) throws InputMismatchException {
        if (r > n || n < 0 || r < 0) {
            throw new InputMismatchException("r cannot be greater than n, and neither n nor r can be negative.");
        }
        return factorial(n) / factorial(n - r);
    }
}
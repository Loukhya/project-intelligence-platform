package com.mathutil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FactorialServiceTest {

    private FactorialService factorialService = new FactorialService();

    @Test
    void testCalculateFactorial_BaseCase_NZero() {
        long result = factorialService.calculateFactorial(0);
        assertEquals(1, result);
    }

    @Test
    void testCalculateFactorial_BaseCase_NOne() {
        long result = factorialService.calculateFactorial(1);
        assertEquals(1, result);
    }

    @Test
    void testCalculateFactorial_ValidInput() {
        for (int i = 2; i <= 20; i++) {
            long expectedResult = calculateExpectedFactorial(i);
            long result = factorialService.calculateFactorial(i);
            assertEquals(expectedResult, result);
        }
    }

    @Test
    void testCalculateFactorial_InvalidInputNegative() {
        assertThrows(IllegalArgumentException.class, () -> factorialService.calculateFactorial(-1));
    }

    private long calculateExpectedFactorial(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
}
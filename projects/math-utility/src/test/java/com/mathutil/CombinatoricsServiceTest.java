package com.mathutil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.InputMismatchException;

public class CombinatoricsServiceTest {

    private CombinatoricsService combinatoricsService = new CombinatoricsService();

    @Test
    public void testNCrValidInputs() {
        long result1 = combinatoricsService.nCr(5, 2);
        long result2 = combinatoricsService.nCr(10, 5);
        assertEquals(10, result1);
        assertEquals(252, result2);
    }

    @Test
    public void testNPrValidInputs() {
        long result1 = combinatoricsService.nPr(5, 2);
        long result2 = combinatoricsService.nPr(10, 5);
        assertEquals(20, result1);
        assertEquals(30240, result2);
    }

    @Test
    public void testNCrInvalidInputsRGreaterThanN() {
        InputMismatchException exception = assertThrows(InputMismatchException.class, () -> combinatoricsService.nCr(5, 6));
        assertEquals("r cannot be greater than n, and neither n nor r can be negative.", exception.getMessage());
    }

    @Test
    public void testNPrInvalidInputsRGreaterThanN() {
        InputMismatchException exception = assertThrows(InputMismatchException.class, () -> combinatoricsService.nPr(5, 6));
        assertEquals("r cannot be greater than n, and neither n nor r can be negative.", exception.getMessage());
    }

    @Test
    public void testNCrInvalidInputsNegativeN() {
        InputMismatchException exception = assertThrows(InputMismatchException.class, () -> combinatoricsService.nCr(-5, 2));
        assertEquals("r cannot be greater than n, and neither n nor r can be negative.", exception.getMessage());
    }

    @Test
    public void testNPrInvalidInputsNegativeN() {
        InputMismatchException exception = assertThrows(InputMismatchException.class, () -> combinatoricsService.nPr(-5, 2));
        assertEquals("r cannot be greater than n, and neither n nor r can be negative.", exception.getMessage());
    }

    @Test
    public void testNCrInvalidInputsNegativeR() {
        InputMismatchException exception = assertThrows(InputMismatchException.class, () -> combinatoricsService.nCr(5, -2));
        assertEquals("r cannot be greater than n, and neither n nor r can be negative.", exception.getMessage());
    }

    @Test
    public void testNPrInvalidInputsNegativeR() {
        InputMismatchException exception = assertThrows(InputMismatchException.class, () -> combinatoricsService.nPr(5, -2));
        assertEquals("r cannot be greater than n, and neither n nor r can be negative.", exception.getMessage());
    }
}
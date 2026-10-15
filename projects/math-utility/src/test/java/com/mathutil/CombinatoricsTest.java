package com.mathutil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.InputMismatchException;
import org.junit.jupiter.api.DisplayName;

public class CombinatoricsTest {

    private CombinatoricsService combinatoricsService = new CombinatoricsService();

    @Test
    @DisplayName("Test nCr with valid input")
    public void test_nCr_valid_input() {
        long result = combinatoricsService.nCr(5, 3);
        long expected = 10;
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test nPr with valid input")
    public void test_nPr_valid_input() {
        long result = combinatoricsService.nPr(5, 3);
        long expected = 60;
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Test nCr with invalid input")
    public void test_nCr_invalid_input() {
        assertThrows(InputMismatchException.class, () -> combinatoricsService.nCr(3, 5));
        assertThrows(InputMismatchException.class, () -> combinatoricsService.nCr(-1, 2));
        assertThrows(InputMismatchException.class, () -> combinatoricsService.nCr(2, -1));
    }

    @Test
    @DisplayName("Test nPr with invalid input")
    public void test_nPr_invalid_input() {
        assertThrows(InputMismatchException.class, () -> combinatoricsService.nPr(3, 5));
        assertThrows(InputMismatchException.class, () -> combinatoricsService.nPr(-1, 2));
        assertThrows(InputMismatchException.class, () -> combinatoricsService.nPr(2, -1));
    }
}
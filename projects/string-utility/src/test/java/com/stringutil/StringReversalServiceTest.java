package com.stringutil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StringReversalServiceTest {

    private StringReversalService stringReversalService = new StringReversalService();

    @Test
    void testReverseHello() {
        // Arrange and Act
        String result = stringReversalService.reverse("hello");
        // Assert
        assertEquals("olleh", result);
    }

    @Test
    void testReverseEmptyString() {
        // Arrange and Act
        String result = stringReversalService.reverse("");
        // Assert
        assertEquals("", result);
    }

    @Test
    void testReverseSingleCharacter() {
        // Arrange and Act
        String result = stringReversalService.reverse("a");
        // Assert
        assertEquals("a", result);
    }

    @Test
    void testReverseStringWithWhitespace() {
        // Arrange and Act
        String result = stringReversalService.reverse("hello world");
        // Assert
        assertEquals("dlrow olleh", result);
    }

    @Test
    void testReverseNullString() {
        // Act and Assert
        assertThrows(NullPointerException.class, () -> stringReversalService.reverse(null));
    }
}

package com.stringutil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StringPaddingTest {

    @Test
    public void testPadLeft_PaddingAdded() {
        StringPadding stringPadding = new StringPadding();
        String actual = stringPadding.padLeft("abc", 5, 'x');
        assertEquals("xxabc", actual);
    }

    @Test
    public void testPadRight_PaddingAdded() {
        StringPadding stringPadding = new StringPadding();
        String actual = stringPadding.padRight("abc", 5, 'y');
        assertEquals("abcyy", actual);
    }

    @Test
    public void testPadLeft_NoPaddingNeeded() {
        StringPadding stringPadding = new StringPadding();
        String actual = stringPadding.padLeft("abc", 3, 'x');
        assertEquals("abc", actual);
    }

    @Test
    public void testPadRight_NoPaddingNeeded() {
        StringPadding stringPadding = new StringPadding();
        String actual = stringPadding.padRight("abc", 3, 'y');
        assertEquals("abc", actual);
    }

    @Test
    public void testPadLeft_NullStringThrowsException() {
        StringPadding stringPadding = new StringPadding();
        NullPointerException exception = assertThrows(NullPointerException.class, () -> stringPadding.padLeft(null, 5, 'x'));
        assertEquals("String cannot be null", exception.getMessage());
    }

    @Test
    public void testPadRight_NullStringThrowsException() {
        StringPadding stringPadding = new StringPadding();
        NullPointerException exception = assertThrows(NullPointerException.class, () -> stringPadding.padRight(null, 5, 'y'));
        assertEquals("String cannot be null", exception.getMessage());
    }
}
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.InputMismatchException;

public class CombinatoricsServiceTest {

    private CombinatoricsService combinatoricsService = new CombinatoricsService();

    @Test
    void testNCr() {
        assertEquals(1, combinatoricsService.nCr(0, 0));
        assertEquals(1, combinatoricsService.nCr(1, 0));
        assertEquals(1, combinatoricsService.nCr(1, 1));
        assertEquals(2, combinatoricsService.nCr(2, 1));
        assertEquals(3, combinatoricsService.nCr(3, 1));
        assertEquals(3, combinatoricsService.nCr(3, 2));
        assertEquals(6, combinatoricsService.nCr(4, 2));
        assertEquals(10, combinatoricsService.nCr(5, 2));
    }

    @Test
    void testNCrInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> combinatoricsService.nCr(1, 2));
        assertThrows(IllegalArgumentException.class, () -> combinatoricsService.nCr(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> combinatoricsService.nCr(1, -1));
    }

    @Test
    void testNPr() {
        assertEquals(1, combinatoricsService.nPr(0, 0));
        assertEquals(1, combinatoricsService.nPr(1, 0));
        assertEquals(1, combinatoricsService.nPr(1, 1));
        assertEquals(2, combinatoricsService.nPr(2, 1));
        assertEquals(3, combinatoricsService.nPr(3, 1));
        assertEquals(6, combinatoricsService.nPr(3, 2));
        assertEquals(12, combinatoricsService.nPr(4, 2));
        assertEquals(60, combinatoricsService.nPr(5, 3));
    }

    @Test
    void testNPrInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> combinatoricsService.nPr(1, 2));
        assertThrows(IllegalArgumentException.class, () -> combinatoricsService.nPr(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> combinatoricsService.nPr(1, -1));
    }
}
import java.util.InputMismatchException;

/**
 * Provides combinatorics calculations.
 * @Requirement(id="e39263f0-34b6-4172-9c06-a1d839f0d223")
 * @Task(id="8aed8e91-b395-4f7b-bc0e-57b7d1b37433")
 */
public class CombinatoricsService {

    /**
     * Calculates the factorial of a number.
     * @param n the number to calculate the factorial of
     * @return the factorial of n
     */
    private long calculateFactorial(int n) {
        if (n < 0) {
            throw new InputMismatchException("Input must be a non-negative integer");
        }
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    /**
     * Calculates the combination (nCr) of two numbers.
     * @param n the total number of items
     * @param r the number of items to choose
     * @return the combination (nCr)
     * @throws IllegalArgumentException if r > n or inputs are negative
     */
    public long nCr(int n, int r) {
        if (r > n || n < 0 || r < 0) {
            throw new IllegalArgumentException("r cannot be greater than n, and inputs cannot be negative");
        }
        return calculateFactorial(n) / (calculateFactorial(r) * calculateFactorial(n - r));
    }

    /**
     * Calculates the permutation (nPr) of two numbers.
     * @param n the total number of items
     * @param r the number of items to choose
     * @return the permutation (nPr)
     * @throws IllegalArgumentException if r > n or inputs are negative
     */
    public long nPr(int n, int r) {
        if (r > n || n < 0 || r < 0) {
            throw new IllegalArgumentException("r cannot be greater than n, and inputs cannot be negative");
        }
        return calculateFactorial(n) / calculateFactorial(n - r);
    }
}
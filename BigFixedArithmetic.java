import java.math.BigInteger;
import java.util.Scanner;

/**
 * BigFixedArithmetic
 * 
 * Demonstrates exact, soft-emulated integer arithmetic using BigInteger.
 * Implements addition, subtraction, Russian Peasant multiplication, and a
 * streaming division engine that outputs digits sequentially without truncation.
 */
public class BigFixedArithmetic {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE  = BigInteger.ONE;
    private static final BigInteger TEN  = BigInteger.valueOf(10);

    // ── Addition & Subtraction ───────────────────────────────────────────────

    /**
     * Adds two BigInteger operands: a + b
     */
    public static BigInteger add(BigInteger a, BigInteger b) {
        return a.add(b);
    }

    /**
     * Subtracts two BigInteger operands: a - b
     */
    public static BigInteger subtract(BigInteger a, BigInteger b) {
        return a.subtract(b);
    }

    // ── Multiplication (Russian Peasant Algorithm) ───────────────────────────

    /**
     * Multiplies two BigInteger values using the Russian Peasant (binary shift-and-add) algorithm.
     * Computes a * b using only bit tests, bit shifts, and addition.
     */
    public static BigInteger multiply(BigInteger a, BigInteger b) {
        if (a.equals(ZERO) || b.equals(ZERO)) {
            return ZERO;
        }

        // Determine sign of the result
        boolean isNegative = (a.signum() < 0) ^ (b.signum() < 0);

        BigInteger ta = a.abs();
        BigInteger tb = b.abs();
        BigInteger result = ZERO;

        while (tb.compareTo(ZERO) > 0) {
            // If the current bit of b is 1, add ta to accumulated result
            if (tb.testBit(0)) {
                result = result.add(ta);
            }
            // Double ta (shift left 1) and halve tb (shift right 1)
            ta = ta.add(ta);
            tb = tb.shiftRight(1);
        }

        return isNegative ? result.negate() : result;
    }

    // ── Division & Helpers ───────────────────────────────────────────────────

    /**
     * Division via shift-and-subtract binary long division.
     * Computes dividend / divisor without hardware division operators.
     */
    public static BigInteger longDivide(BigInteger dividend, BigInteger divisor) {
        if (divisor.equals(ZERO)) {
            throw new ArithmeticException("Division by zero");
        }

        BigInteger quotient = ZERO;
        BigInteger rem = dividend;

        BigInteger shifted = divisor;
        BigInteger bit = ONE;

        while (shifted.shiftLeft(1).compareTo(rem) <= 0) {
            shifted = shifted.shiftLeft(1);
            bit = bit.shiftLeft(1);
        }

        while (bit.compareTo(ZERO) > 0) {
            if (rem.compareTo(shifted) >= 0) {
                rem = rem.subtract(shifted);
                quotient = quotient.add(bit);
            }
            shifted = shifted.shiftRight(1);
            bit = bit.shiftRight(1);
        }

        return quotient;
    }

    /**
     * Divides {@code dividend} by {@code divisor} and continuously outputs decimal digits.
     * 
     * @param dividend numerator
     * @param divisor denominator
     * @param maxDecimalPlaces number of fractional digits to print, or -1 for unlimited streaming
     */
    public static void streamDivide(BigInteger dividend, BigInteger divisor, long maxDecimalPlaces) {
        if (divisor.equals(ZERO)) {
            throw new ArithmeticException("Division by zero");
        }

        boolean isNegative = (dividend.signum() < 0) ^ (divisor.signum() < 0);
        BigInteger rem = dividend.abs();
        BigInteger den = divisor.abs();

        if (isNegative && !rem.equals(ZERO)) {
            System.out.print("-");
        }

        // 1. Integer Part via Binary Long Division
        BigInteger integerPart = longDivide(rem, den);
        System.out.print(integerPart);

        // Update remainder: rem = rem - (integerPart * den)
        rem = rem.subtract(multiply(integerPart, den));

        if (rem.equals(ZERO) || maxDecimalPlaces == 0) {
            System.out.println();
            return;
        }

        // 2. Continuous Fractional Digit Generator
        System.out.print(".");
        long digitsOutput = 0;

        while (!rem.equals(ZERO)) {
            if (maxDecimalPlaces >= 0 && digitsOutput >= maxDecimalPlaces) {
                break;
            }

            // Bring down next decimal digit: rem = rem * 10
            rem = multiply(rem, TEN);

            // Determine next single digit using binary long division
            BigInteger digit = longDivide(rem, den);
            System.out.print(digit);

            // Update remainder: rem = rem - (digit * den)
            rem = rem.subtract(multiply(digit, den));

            digitsOutput++;
        }

        System.out.println();
    }

    // ── Interactive Program Entry Point ──────────────────────────────────────

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== BigFixedArithmetic Demo ===");
        System.out.print("Enter Dividend / First Operand: ");
        BigInteger a = new BigInteger(scanner.next());

        System.out.print("Enter Divisor / Second Operand: ");
        BigInteger b = new BigInteger(scanner.next());

        System.out.println("\n--- Basic Operations ---");
        System.out.println("Addition (a + b)       : " + add(a, b));
        System.out.println("Subtraction (a - b)    : " + subtract(a, b));
        System.out.println("Multiplication (a * b) : " + multiply(a, b));

        System.out.println("\n--- Streaming Division ---");
        System.out.print("Enter required decimal places (-1 for unlimited streaming): ");
        long precision = scanner.nextLong();

        System.out.print("Division Result (a / b): ");
        streamDivide(a, b, precision);

        scanner.close();
    }
}

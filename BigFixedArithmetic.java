import java.math.BigInteger;
import java.util.Scanner;

/**
 * BigFixedArithmetic
 * 
 * Demonstrates exact, soft-emulated integer arithmetic using BigInteger.
 * Implements addition, subtraction, Russian Peasant multiplication, and a
 * streaming division engine that outputs digits sequentially without truncation.
 *
 * Numbers can be supplied as strings (see {@link #parseDecimal(String)}).  A
 * decimal such as "-12.375" is held exactly as an unscaled integer plus a scale
 * (-12375 and 3), so no precision is lost on input.
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

    // ── String Parsing ───────────────────────────────────────────────────────

    /**
     * An exact decimal number: value = unscaled / 10^scale.
     * For example "-12.375" is unscaled = -12375, scale = 3.
     */
    public static final class Decimal {
        public final BigInteger unscaled;
        public final int scale;

        public Decimal(BigInteger unscaled, int scale) {
            this.unscaled = unscaled;
            this.scale = scale;
        }

        /** Exact decimal text, e.g. unscaled -12375 / scale 3 -> "-12.375". */
        @Override
        public String toString() {
            String digits = unscaled.abs().toString();
            if (scale > 0) {
                StringBuilder padded = new StringBuilder(digits);
                while (padded.length() <= scale) {
                    padded.insert(0, '0');
                }
                padded.insert(padded.length() - scale, '.');
                digits = padded.toString();
            }
            return (unscaled.signum() < 0 ? "-" : "") + digits;
        }
    }

    /**
     * Parses a decimal string into an exact {@link Decimal}.
     *
     * Accepted format: optional leading '+' or '-', digits, and at most one
     * decimal point, with at least one digit overall.  Examples: "42",
     * "-0.5", "+3.14159", ".25", "7.".  Surrounding whitespace is ignored.
     * Every digit is kept; nothing is rounded or truncated.
     *
     * Digits are accumulated as value = value * 10 + digit, using this
     * class's own {@link #multiply} and {@link #add}.
     *
     * @throws IllegalArgumentException if the string is null, empty, has no
     *         digits, has more than one decimal point, or contains any other
     *         character (including exponents such as "1e5")
     */
    public static Decimal parseDecimal(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Input string must not be null");
        }
        String s = text.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Input string must not be empty");
        }

        int i = 0;
        boolean negative = false;
        if (s.charAt(0) == '-') {
            negative = true;
            i++;
        } else if (s.charAt(0) == '+') {
            i++;
        }

        BigInteger value = ZERO;
        int scale = 0;
        int digitCount = 0;
        boolean seenDot = false;

        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (seenDot) {
                    throw new IllegalArgumentException("Multiple decimal points in \"" + text + "\"");
                }
                seenDot = true;
            } else if (c >= '0' && c <= '9') {
                value = add(multiply(value, TEN), BigInteger.valueOf(c - '0'));
                digitCount++;
                if (seenDot) {
                    scale++;
                }
            } else {
                throw new IllegalArgumentException("Invalid character '" + c + "' in \"" + text + "\"");
            }
        }

        if (digitCount == 0) {
            throw new IllegalArgumentException("No digits in \"" + text + "\"");
        }
        return new Decimal(negative ? value.negate() : value, scale);
    }

    /**
     * Parses a whole-number string (optional sign, then digits only) into a BigInteger.
     *
     * @throws IllegalArgumentException if the string is not a plain integer
     */
    public static BigInteger parseInteger(String text) {
        Decimal d = parseDecimal(text);
        if (d.scale != 0) {
            throw new IllegalArgumentException("Not a whole number: \"" + text + "\"");
        }
        return d.unscaled;
    }

    // ── Exact Decimal Operations ─────────────────────────────────────────────

    /** Returns 10^n (n >= 0) built with this class's multiply. */
    private static BigInteger pow10(int n) {
        BigInteger result = ONE;
        for (int k = 0; k < n; k++) {
            result = multiply(result, TEN);
        }
        return result;
    }

    /** Rescales an unscaled value from scale {@code from} up to scale {@code to} (to >= from). */
    private static BigInteger rescale(BigInteger unscaled, int from, int to) {
        return multiply(unscaled, pow10(to - from));
    }

    /** Exact a + b. */
    public static Decimal add(Decimal a, Decimal b) {
        int scale = Math.max(a.scale, b.scale);
        return new Decimal(add(rescale(a.unscaled, a.scale, scale),
                               rescale(b.unscaled, b.scale, scale)), scale);
    }

    /** Exact a - b. */
    public static Decimal subtract(Decimal a, Decimal b) {
        int scale = Math.max(a.scale, b.scale);
        return new Decimal(subtract(rescale(a.unscaled, a.scale, scale),
                                    rescale(b.unscaled, b.scale, scale)), scale);
    }

    /** Exact a * b. */
    public static Decimal multiply(Decimal a, Decimal b) {
        return new Decimal(multiply(a.unscaled, b.unscaled), a.scale + b.scale);
    }

    /**
     * Streams a / b for decimal operands.  Since
     *   (ua / 10^sa) / (ub / 10^sb) = (ua * 10^sb) / (ub * 10^sa),
     * the quotient is reduced to an exact integer division and handed to
     * {@link #streamDivide(BigInteger, BigInteger, long)}.
     */
    public static void streamDivide(Decimal a, Decimal b, long maxDecimalPlaces) {
        BigInteger numerator   = multiply(a.unscaled, pow10(b.scale));
        BigInteger denominator = multiply(b.unscaled, pow10(a.scale));
        streamDivide(numerator, denominator, maxDecimalPlaces);
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
        System.out.println("Numbers may be integers or decimals, e.g. 42, -0.5, 3.14159");

        Decimal a;
        Decimal b;
        try {
            System.out.print("Enter Dividend / First Operand: ");
            a = parseDecimal(scanner.next());

            System.out.print("Enter Divisor / Second Operand: ");
            b = parseDecimal(scanner.next());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid number: " + e.getMessage());
            scanner.close();
            return;
        }

        System.out.println("\n--- Basic Operations ---");
        System.out.println("Addition (a + b)       : " + add(a, b));
        System.out.println("Subtraction (a - b)    : " + subtract(a, b));
        System.out.println("Multiplication (a * b) : " + multiply(a, b));

        System.out.println("\n--- Streaming Division ---");
        System.out.print("Enter required decimal places (-1 for unlimited streaming): ");
        long precision = scanner.nextLong();

        System.out.print("Division Result (a / b): ");
        try {
            streamDivide(a, b, precision);
        } catch (ArithmeticException e) {
            System.out.println();
            System.out.println("Error: " + e.getMessage());
        }

        scanner.close();
    }
}

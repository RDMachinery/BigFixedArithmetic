import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * BigFixedArithmetic
 *
 * Demonstrates exact, soft-emulated integer arithmetic using BigInteger.
 * Implements addition, subtraction, Russian Peasant multiplication, and a
 * division engine that outputs digits sequentially.
 *
 * Numbers can be supplied as strings (see {@link #parseDecimal(String)}).  A
 * decimal such as "-12.375" is held exactly as an unscaled integer plus a scale
 * (-12375 and 3), so no precision is lost on input.
 *
 * Division has two modes:
 * <ul>
 *   <li><b>Exact (maxDecimalPlaces = -1):</b> the full result is produced, and a
 *       repeating decimal is shown with its repeating block in parentheses,
 *       e.g. 1/3 = 0.(3), 1/6 = 0.1(6), 22/7 = 3.(142857).  This mode always
 *       terminates, and digits are emitted as they are computed.</li>
 *   <li><b>Fixed places (maxDecimalPlaces &gt;= 0):</b> the result is rounded
 *       to that many places using a {@link RoundingMode} (default HALF_UP).</li>
 * </ul>
 */
public class BigFixedArithmetic {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE  = BigInteger.ONE;
    private static final BigInteger FIVE = BigInteger.valueOf(5);
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
     * Prints a / b for decimal operands, rounding with HALF_UP.  Since
     *   (ua / 10^sa) / (ub / 10^sb) = (ua * 10^sb) / (ub * 10^sa),
     * the quotient is reduced to an exact integer division and handed to
     * {@link #streamDivide(BigInteger, BigInteger, long, RoundingMode)}.
     */
    public static void streamDivide(Decimal a, Decimal b, long maxDecimalPlaces) {
        streamDivide(a, b, maxDecimalPlaces, RoundingMode.HALF_UP);
    }

    /** Prints a / b for decimal operands with the given rounding mode. */
    public static void streamDivide(Decimal a, Decimal b, long maxDecimalPlaces, RoundingMode mode) {
        streamDivide(divisionNumerator(a, b), divisionDenominator(a, b), maxDecimalPlaces, mode);
    }

    /** Returns a / b for decimal operands as a string (see {@link #divideToString}). */
    public static String divideToString(Decimal a, Decimal b, long maxDecimalPlaces, RoundingMode mode) {
        return divideToString(divisionNumerator(a, b), divisionDenominator(a, b), maxDecimalPlaces, mode);
    }

    private static BigInteger divisionNumerator(Decimal a, Decimal b) {
        return multiply(a.unscaled, pow10(b.scale));
    }

    private static BigInteger divisionDenominator(Decimal a, Decimal b) {
        return multiply(b.unscaled, pow10(a.scale));
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
     * Prints dividend / divisor followed by a newline, rounding with HALF_UP.
     *
     * @param dividend numerator
     * @param divisor denominator
     * @param maxDecimalPlaces number of fractional digits (the result is rounded to
     *        this many places), or -1 for the exact result with any repeating
     *        block shown in parentheses
     */
    public static void streamDivide(BigInteger dividend, BigInteger divisor, long maxDecimalPlaces) {
        streamDivide(dividend, divisor, maxDecimalPlaces, RoundingMode.HALF_UP);
    }

    /**
     * Prints dividend / divisor followed by a newline.
     *
     * In exact mode (-1) digits are printed as they are computed.  In
     * fixed-places mode the result is rounded with {@code mode} and printed
     * once; a result that divides out exactly in fewer places is printed
     * without padding zeros (24.75, not 24.750000).
     *
     * @throws ArithmeticException on division by zero, or if {@code mode} is
     *         UNNECESSARY and the result needs rounding
     */
    public static void streamDivide(BigInteger dividend, BigInteger divisor,
                                    long maxDecimalPlaces, RoundingMode mode) {
        divideInto(System.out::print, dividend, divisor, maxDecimalPlaces, mode);
        System.out.println();
    }

    /** Same as {@link #streamDivide(BigInteger, BigInteger, long, RoundingMode)} but returns the text. */
    public static String divideToString(BigInteger dividend, BigInteger divisor,
                                        long maxDecimalPlaces, RoundingMode mode) {
        StringBuilder sb = new StringBuilder();
        divideInto(s -> sb.append(s), dividend, divisor, maxDecimalPlaces, mode);
        return sb.toString();
    }

    private static void divideInto(Consumer<String> out, BigInteger dividend, BigInteger divisor,
                                   long maxDecimalPlaces, RoundingMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (divisor.equals(ZERO)) {
            throw new ArithmeticException("Division by zero");
        }
        if (maxDecimalPlaces < -1) {
            throw new IllegalArgumentException("maxDecimalPlaces must be -1 (exact) or >= 0");
        }

        boolean isNegative = (dividend.signum() < 0) ^ (divisor.signum() < 0);
        BigInteger rem = dividend.abs();
        BigInteger den = divisor.abs();

        // Integer part via binary long division, then the remainder below the divisor
        BigInteger integerPart = longDivide(rem, den);
        rem = rem.subtract(multiply(integerPart, den));

        if (maxDecimalPlaces < 0) {
            exactDigits(out, isNegative && !dividend.equals(ZERO), integerPart, rem, den);
        } else {
            roundedDigits(out, isNegative, integerPart, rem, den, maxDecimalPlaces, mode);
        }
    }

    /**
     * Exact mode.  Emits every digit as it is computed and marks the repeating block.
     *
     * The remainders of long division eventually cycle.  For the reduced fraction
     * rem/den, the digits before the cycle number max(twos, fives), where those are
     * the powers of 2 and 5 in the reduced denominator, and the cycle is present
     * only if other prime factors remain.  So "(" can be printed at the right moment
     * without buffering, and the cycle ends the first time the remainder returns to
     * the value it had when "(" was printed.  Memory use is constant.
     */
    private static void exactDigits(Consumer<String> out, boolean negative,
                                    BigInteger integerPart, BigInteger rem, BigInteger den) {
        if (negative) {
            out.accept("-");
        }
        out.accept(integerPart.toString());
        if (rem.equals(ZERO)) {
            return;
        }
        out.accept(".");

        // Reduce rem/den and count the factors of 2 and 5 in the denominator
        BigInteger reduced = longDivide(den, rem.gcd(den));
        long twos = 0;
        long fives = 0;
        while (!reduced.testBit(0)) {
            reduced = reduced.shiftRight(1);
            twos++;
        }
        while (true) {
            BigInteger q = longDivide(reduced, FIVE);
            if (!multiply(q, FIVE).equals(reduced)) {
                break;
            }
            reduced = q;
            fives++;
        }
        boolean repeating = !reduced.equals(ONE);
        long preLength = Math.max(twos, fives);

        BigInteger cycleStart = null;
        long printed = 0;
        while (true) {
            if (repeating && printed == preLength) {
                out.accept("(");
                cycleStart = rem;
            }
            if (!repeating && rem.equals(ZERO)) {
                break;                                   // terminating decimal
            }

            // Bring down next decimal digit: rem = rem * 10
            rem = multiply(rem, TEN);
            BigInteger digit = longDivide(rem, den);
            out.accept(digit.toString());
            rem = rem.subtract(multiply(digit, den));
            printed++;

            if (cycleStart != null && rem.equals(cycleStart)) {
                out.accept(")");
                break;
            }
        }
    }

    /**
     * Fixed-places mode.  Generates up to maxDecimalPlaces digits (stopping early
     * if the division comes out exact), then rounds using the leftover remainder.
     */
    private static void roundedDigits(Consumer<String> out, boolean negative,
                                      BigInteger integerPart, BigInteger rem, BigInteger den,
                                      long maxDecimalPlaces, RoundingMode mode) {
        BigInteger scaled = integerPart;     // value * 10^places, truncated
        long places = 0;
        while (!rem.equals(ZERO) && places < maxDecimalPlaces) {
            rem = multiply(rem, TEN);
            BigInteger digit = longDivide(rem, den);
            rem = rem.subtract(multiply(digit, den));
            scaled = add(multiply(scaled, TEN), digit);
            places++;
        }

        if (!rem.equals(ZERO) && roundsUp(mode, negative, scaled, rem, den)) {
            scaled = scaled.add(ONE);
        }

        String digits = scaled.toString();
        int p = (int) places;
        if (p > 0) {
            StringBuilder sb = new StringBuilder(digits);
            while (sb.length() <= p) {
                sb.insert(0, '0');
            }
            sb.insert(sb.length() - p, '.');
            digits = sb.toString();
        }
        out.accept((negative && !scaled.equals(ZERO) ? "-" : "") + digits);
    }

    /**
     * Decides whether the magnitude is bumped up by one unit in the last place.
     * {@code rem}/{@code den} is the unused fraction (0 &lt; rem &lt; den) and
     * {@code scaled} is the truncated magnitude.
     */
    private static boolean roundsUp(RoundingMode mode, boolean negative,
                                    BigInteger scaled, BigInteger rem, BigInteger den) {
        switch (mode) {
            case UP:          return true;
            case DOWN:        return false;
            case CEILING:     return !negative;
            case FLOOR:       return negative;
            case UNNECESSARY: throw new ArithmeticException("Rounding necessary");
            default:
                int half = add(rem, rem).compareTo(den);     // compares 2*rem with den
                if (mode == RoundingMode.HALF_UP)   return half >= 0;
                if (mode == RoundingMode.HALF_DOWN) return half > 0;
                return half > 0 || (half == 0 && scaled.testBit(0));   // HALF_EVEN
        }
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

        System.out.println("\n--- Division ---");
        System.out.print("Enter required decimal places (-1 for exact, repeating blocks shown like 0.(3)): ");
        long precision = scanner.nextLong();

        RoundingMode mode = RoundingMode.HALF_UP;
        if (precision >= 0) {
            scanner.nextLine();      // rest of the line after the number
            System.out.print("Rounding mode (HALF_UP, HALF_EVEN, HALF_DOWN, UP, DOWN, CEILING, FLOOR) [HALF_UP]: ");
            String choice = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (!choice.isEmpty()) {
                try {
                    mode = RoundingMode.valueOf(choice.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    System.out.println("Unknown rounding mode: " + choice);
                    scanner.close();
                    return;
                }
            }
        }

        System.out.print("Division Result (a / b): ");
        try {
            streamDivide(a, b, precision, mode);
        } catch (ArithmeticException | IllegalArgumentException e) {
            System.out.println();
            System.out.println("Error: " + e.getMessage());
        }

        scanner.close();
    }
}

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * BigFixedArithmetic
 *
 * Exact arithmetic built on BigInteger, with the core operations written out
 * the long way: addition, subtraction, Russian Peasant multiplication and
 * shift-and-subtract long division.
 *
 * <h2>Numbers from strings</h2>
 * {@link #parseDecimal(String)} reads a decimal string exactly, including
 * scientific notation ("1.5e3", "2E-4").  A decimal such as "-12.375" is held
 * as an unscaled integer plus a scale (-12375 and 3), so no precision is lost
 * on input.
 *
 * <h2>Division</h2>
 * <ul>
 *   <li><b>Exact (maxDecimalPlaces = -1):</b> the full result is produced, and a
 *       repeating expansion is shown with its repeating block in parentheses,
 *       e.g. 1/3 = 0.(3), 1/6 = 0.1(6), 22/7 = 3.(142857).  This mode always
 *       terminates, and digits are emitted as they are computed.</li>
 *   <li><b>Fixed places (maxDecimalPlaces &gt;= 0):</b> the result is rounded
 *       to that many places using a {@link RoundingMode} (default HALF_UP).</li>
 * </ul>
 * Both modes work in any base from 2 to 36.
 *
 * <h2>{@link Rational}</h2>
 * An exact fraction type, and a {@link Number} subclass, with arithmetic,
 * exact comparison, floor/ceiling/rounding, parsing of fractions, mixed
 * numbers, repeating decimals and scientific notation, repeating-cycle
 * analysis, expansion in any base, continued fractions, best approximations,
 * mixed-number and Egyptian-fraction formatting, scientific and engineering
 * output, bounded-precision arithmetic through {@link Rational.Context}, and
 * square roots, nth roots and powers (including fractional exponents) that are
 * correctly rounded to a chosen precision.
 */
public class BigFixedArithmetic {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE  = BigInteger.ONE;
    private static final BigInteger TEN  = BigInteger.valueOf(10);

    /**
     * Largest exponent accepted in scientific notation, in either direction.
     * Numbers are held as full integers, so 1e100000 would need 100000 digits.
     */
    public static final int MAX_EXPONENT = 10_000;

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
     * An exact decimal number: value = unscaled / 10^scale (scale is never
     * negative).  For example "-12.375" is unscaled = -12375, scale = 3.
     * A {@link Number}, so it converts to int, long, float and double.
     */
    public static final class Decimal extends Number {
        private static final long serialVersionUID = 1L;

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

        /** Scientific notation with the fewest digits, e.g. "-1.2375E+1" ("0E+0" for zero). */
        public String toScientificString() {
            return scientific(false);
        }

        /** Engineering notation (exponent a multiple of 3), e.g. "12.375E+0". */
        public String toEngineeringString() {
            return scientific(true);
        }

        private String scientific(boolean engineering) {
            if (unscaled.signum() == 0) {
                return "0E+0";
            }
            String full = unscaled.abs().toString();
            return formatScientific(unscaled.signum() < 0 ? "-" : "", stripTrailingZeros(full),
                                    (long) full.length() - 1 - scale, engineering);
        }

        @Override public int intValue()       { return Rational.of(this).intValue(); }
        @Override public long longValue()     { return Rational.of(this).longValue(); }
        @Override public float floatValue()   { return Rational.of(this).floatValue(); }
        @Override public double doubleValue() { return Rational.of(this).doubleValue(); }
    }

    /**
     * Parses a decimal string into an exact {@link Decimal}.
     *
     * Accepted format: optional leading '+' or '-', digits, at most one decimal
     * point, and an optional exponent ('e' or 'E', optional sign, digits), with
     * at least one digit in the number.  Examples: "42", "-0.5", "+3.14159",
     * ".25", "7.", "1.5e3", "2E-4".  Surrounding whitespace is ignored.  Every
     * digit is kept; nothing is rounded or truncated.  The exponent may not be
     * larger than {@link #MAX_EXPONENT} in either direction.
     *
     * Digits are accumulated as value = value * 10 + digit, using this
     * class's own {@link #multiply} and {@link #add}.
     *
     * @throws IllegalArgumentException if the string is null, empty, has no
     *         digits, has more than one decimal point, has a malformed or
     *         oversized exponent, or contains any other character
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
        int exponent = 0;
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
            } else if (c == 'e' || c == 'E') {
                exponent = parseExponent(s.substring(i + 1), text);
                break;
            } else {
                throw new IllegalArgumentException("Invalid character '" + c + "' in \"" + text + "\"");
            }
        }

        if (digitCount == 0) {
            throw new IllegalArgumentException("No digits in \"" + text + "\"");
        }

        BigInteger unscaled = negative ? value.negate() : value;
        int newScale = scale - exponent;
        if (newScale < 0) {
            unscaled = multiply(unscaled, pow10(-newScale));
            newScale = 0;
        }
        return new Decimal(unscaled, newScale);
    }

    /**
     * Parses a whole-number string (optional sign, then digits only) into a BigInteger.
     * A string with an exponent is accepted if the value is a whole number ("1e3").
     *
     * @throws IllegalArgumentException if the string is not a whole number
     */
    public static BigInteger parseInteger(String text) {
        Decimal d = parseDecimal(text);
        if (d.scale != 0) {
            throw new IllegalArgumentException("Not a whole number: \"" + text + "\"");
        }
        return d.unscaled;
    }

    /** Parses the text after an 'e': optional sign, then digits, at most MAX_EXPONENT in size. */
    private static int parseExponent(String t, String original) {
        int j = 0;
        boolean negative = false;
        if (j < t.length() && (t.charAt(j) == '+' || t.charAt(j) == '-')) {
            negative = t.charAt(j) == '-';
            j++;
        }
        if (j >= t.length()) {
            throw new IllegalArgumentException("Missing exponent digits in \"" + original + "\"");
        }
        long value = 0;
        for (; j < t.length(); j++) {
            char c = t.charAt(j);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Invalid character '" + c + "' in the exponent of \"" + original + "\"");
            }
            value = value * 10 + (c - '0');
            if (value > MAX_EXPONENT) {
                throw new IllegalArgumentException("Exponent is larger than " + MAX_EXPONENT + " in \"" + original + "\"");
            }
        }
        return (int) (negative ? -value : value);
    }

    // ── Scientific / engineering formatting ──────────────────────────────────

    private static String stripTrailingZeros(String digits) {
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') {
            end--;
        }
        return digits.substring(0, end);
    }

    /**
     * Lays out significant digits with an exponent: "d.dddE+x", or in
     * engineering form (exponent a multiple of 3) "ddd.dddE+x".  The value is
     * 0.digits scaled so that the first digit has weight 10^exponent.
     */
    private static String formatScientific(String sign, String digits, long exponent, boolean engineering) {
        if (!engineering) {
            String mantissa = digits.length() > 1 ? digits.charAt(0) + "." + digits.substring(1) : digits;
            return sign + mantissa + "E" + (exponent < 0 ? "-" : "+") + Math.abs(exponent);
        }
        long shifted = Math.floorDiv(exponent, 3L) * 3L;
        int intLength = (int) (exponent - shifted) + 1;
        StringBuilder padded = new StringBuilder(digits);
        while (padded.length() < intLength) {
            padded.append('0');
        }
        String intPart = padded.substring(0, intLength);
        String fraction = padded.substring(intLength);
        String mantissa = fraction.isEmpty() ? intPart : intPart + "." + fraction;
        return sign + mantissa + "E" + (shifted < 0 ? "-" : "+") + Math.abs(shifted);
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
     * Both arguments must be non-negative.
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

    /** a mod m for a >= 0 and m > 0, using longDivide and multiply. */
    private static BigInteger modulo(BigInteger a, BigInteger m) {
        return a.subtract(multiply(longDivide(a, m), m));
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
        divideInto(System.out::print, dividend, divisor, maxDecimalPlaces, mode, 10);
        System.out.println();
    }

    /** Same as {@link #streamDivide(BigInteger, BigInteger, long, RoundingMode)} but returns the text. */
    public static String divideToString(BigInteger dividend, BigInteger divisor,
                                        long maxDecimalPlaces, RoundingMode mode) {
        return divideToString(dividend, divisor, maxDecimalPlaces, mode, 10);
    }

    /**
     * Returns dividend / divisor written in the given base (2 to 36, digits 0-9
     * then a-z).  Exact mode and rounding behave exactly as in base 10, for
     * example 1/3 is "0.1" in base 3 and 1/10 is "0.0(0011)" in base 2.
     */
    public static String divideToString(BigInteger dividend, BigInteger divisor,
                                        long maxDecimalPlaces, RoundingMode mode, int radix) {
        StringBuilder sb = new StringBuilder();
        divideInto(s -> sb.append(s), dividend, divisor, maxDecimalPlaces, mode, radix);
        return sb.toString();
    }

    private static void checkRadix(int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new IllegalArgumentException("Base must be between 2 and 36, got " + radix);
        }
    }

    private static void divideInto(Consumer<String> out, BigInteger dividend, BigInteger divisor,
                                   long maxDecimalPlaces, RoundingMode mode, int radix) {
        Objects.requireNonNull(mode, "mode");
        checkRadix(radix);
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

        BigInteger radixBig = BigInteger.valueOf(radix);
        if (maxDecimalPlaces < 0) {
            exactDigits(out, isNegative && !dividend.equals(ZERO), integerPart, rem, den, radixBig);
        } else {
            roundedDigits(out, isNegative, integerPart, rem, den, maxDecimalPlaces, mode, radixBig);
        }
    }

    /**
     * Splits a reduced denominator for a given base.  Returns {core, preLength}:
     * the part of the denominator coprime to the base, and the number of digits
     * before any repeating block starts.  The expansion terminates exactly when
     * core is 1.
     *
     * Each pass divides out the factors the denominator shares with the base;
     * the number of passes is the length of the non-repeating prefix.  In base
     * 10 that is max(power of 2, power of 5) in the denominator.
     */
    private static BigInteger[] splitDenominator(BigInteger reducedDen, BigInteger radix) {
        BigInteger core = reducedDen;
        long passes = 0;
        while (true) {
            BigInteger g = core.gcd(radix);
            if (g.equals(ONE)) {
                break;
            }
            core = longDivide(core, g);
            passes++;
        }
        return new BigInteger[] { core, BigInteger.valueOf(passes) };
    }

    /**
     * Exact mode.  Emits every digit as it is computed and marks the repeating block.
     *
     * The remainders of long division eventually cycle.  The length of the
     * non-repeating prefix and whether a cycle exists at all are known up front
     * (see {@link #splitDenominator}), so "(" can be printed at the right moment
     * without buffering, and the cycle ends the first time the remainder returns
     * to the value it had when "(" was printed.  Memory use is constant.
     */
    private static void exactDigits(Consumer<String> out, boolean negative,
                                    BigInteger integerPart, BigInteger rem, BigInteger den,
                                    BigInteger radix) {
        if (negative) {
            out.accept("-");
        }
        out.accept(integerPart.toString(radix.intValue()));
        if (rem.equals(ZERO)) {
            return;
        }
        out.accept(".");

        BigInteger[] split = splitDenominator(longDivide(den, rem.gcd(den)), radix);
        boolean repeating = !split[0].equals(ONE);
        long preLength = split[1].longValue();

        BigInteger cycleStart = null;
        long printed = 0;
        while (true) {
            if (repeating && printed == preLength) {
                out.accept("(");
                cycleStart = rem;
            }
            if (!repeating && rem.equals(ZERO)) {
                break;                                   // terminating expansion
            }

            // Bring down next digit: rem = rem * radix
            rem = multiply(rem, radix);
            BigInteger digit = longDivide(rem, den);
            out.accept(digit.toString(radix.intValue()));
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
                                      long maxDecimalPlaces, RoundingMode mode, BigInteger radix) {
        BigInteger scaled = integerPart;     // value * radix^places, truncated
        long places = 0;
        while (!rem.equals(ZERO) && places < maxDecimalPlaces) {
            rem = multiply(rem, radix);
            BigInteger digit = longDivide(rem, den);
            rem = rem.subtract(multiply(digit, den));
            scaled = add(multiply(scaled, radix), digit);
            places++;
        }

        if (!rem.equals(ZERO)) {
            boolean lastDigitOdd = modulo(scaled, radix).testBit(0);
            if (roundsUp(mode, negative, lastDigitOdd, rem, den)) {
                scaled = scaled.add(ONE);
            }
        }

        String digits = scaled.toString(radix.intValue());
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
     * {@code lastDigitOdd} says whether the truncated result ends in an odd digit.
     */
    private static boolean roundsUp(RoundingMode mode, boolean negative, boolean lastDigitOdd,
                                    BigInteger rem, BigInteger den) {
        return roundsUpGivenHalf(mode, negative, lastDigitOdd, add(rem, rem).compareTo(den));
    }

    /**
     * The rounding decision given how the discarded fraction compares with one
     * half (negative: below, zero: exactly half, positive: above).  The caller
     * guarantees the fraction is not zero.
     */
    private static boolean roundsUpGivenHalf(RoundingMode mode, boolean negative,
                                             boolean lastDigitOdd, int half) {
        switch (mode) {
            case UP:          return true;
            case DOWN:        return false;
            case CEILING:     return !negative;
            case FLOOR:       return negative;
            case UNNECESSARY: throw new ArithmeticException("Rounding necessary");
            case HALF_UP:     return half >= 0;
            case HALF_DOWN:   return half > 0;
            default:          return half > 0 || (half == 0 && lastDigitOdd);   // HALF_EVEN
        }
    }

    // ── Rational ─────────────────────────────────────────────────────────────

    /**
     * An exact fraction num/den in lowest terms with den &gt; 0.  The sign is
     * carried by the numerator.  Instances are immutable.  A {@link Number}, so
     * it converts to int, long, float and double (float and double are
     * correctly rounded).
     *
     * Inside this class use BigInteger.ZERO / BigInteger.ONE for big-integer
     * constants: ZERO and ONE here are the Rational constants.
     */
    public static final class Rational extends Number implements Comparable<Rational> {
        private static final long serialVersionUID = 1L;

        public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
        public static final Rational ONE  = new Rational(BigInteger.ONE,  BigInteger.ONE);

        public final BigInteger num;
        public final BigInteger den;

        /** Callers must pass a value already in lowest terms with den > 0. */
        private Rational(BigInteger num, BigInteger den) {
            this.num = num;
            this.den = den;
        }

        // ── helpers (not shadowed by this class's own method names) ──────────

        private static BigInteger mul(BigInteger a, BigInteger b) {
            return BigFixedArithmetic.multiply(a, b);
        }

        /** Truncating division by a positive divisor, any sign for the dividend. */
        private static BigInteger quotient(BigInteger a, BigInteger b) {
            return a.signum() < 0 ? longDivide(a.negate(), b).negate() : longDivide(a, b);
        }

        private static BigInteger powInt(BigInteger base, long exponent) {
            BigInteger result = BigInteger.ONE;
            BigInteger b = base;
            long e = exponent;
            while (e > 0) {
                if ((e & 1L) == 1L) {
                    result = mul(result, b);
                }
                e >>= 1;
                if (e > 0) {
                    b = mul(b, b);
                }
            }
            return result;
        }

        /** 10^e for e >= 0. */
        private static BigInteger tenPow(long e) {
            return powInt(TEN, e);
        }

        /** This value scaled by 10^k (k may be negative), exactly. */
        private Rational scaleByPowerOfTen(long k) {
            return k >= 0 ? multiply(of(tenPow(k))) : divide(of(tenPow(-k)));
        }

        // ── construction ─────────────────────────────────────────────────────

        /** The fraction n/d, reduced.  @throws ArithmeticException if d is zero */
        public static Rational of(BigInteger n, BigInteger d) {
            if (d.signum() == 0) {
                throw new ArithmeticException("Denominator is zero");
            }
            if (d.signum() < 0) {
                n = n.negate();
                d = d.negate();
            }
            BigInteger g = n.gcd(d);
            if (!g.equals(BigInteger.ONE)) {
                n = quotient(n, g);
                d = quotient(d, g);
            }
            return new Rational(n, d);
        }

        public static Rational of(BigInteger n) {
            return new Rational(n, BigInteger.ONE);
        }

        public static Rational of(long n) {
            return of(BigInteger.valueOf(n));
        }

        public static Rational of(long n, long d) {
            return of(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        /** The exact value of a parsed {@link Decimal}. */
        public static Rational of(Decimal d) {
            return of(d.unscaled, pow10(d.scale));
        }

        // ── parsing ──────────────────────────────────────────────────────────

        /**
         * Parses a number written in any of these forms:
         * <ul>
         *   <li>integers and decimals: {@code 42}, {@code -0.5}, {@code .25}</li>
         *   <li>scientific notation: {@code 1e5}, {@code -2.5E-3}, {@code 1.5e+2}</li>
         *   <li>repeating decimals: {@code 0.(3)}, {@code 0.1(6)}, {@code 3.(142857)},
         *       also with an exponent, {@code 0.(3)e2}</li>
         *   <li>fractions: {@code 22/7}, {@code -7/2}, {@code 1.5/0.25}, {@code 1e3/4},
         *       {@code 0.(3)/2}</li>
         *   <li>mixed numbers: {@code 3 1/7}, {@code -3 1/7} (the sign applies to the whole number)</li>
         * </ul>
         * The value is exact: {@code 0.(3)} is exactly 1/3 and {@code 0.(9)} is exactly 1.
         * An exponent may not be larger than {@link #MAX_EXPONENT} in either direction.
         *
         * @throws IllegalArgumentException if the text is not in one of these forms
         * @throws ArithmeticException if a fraction has a zero denominator
         */
        public static Rational parse(String text) {
            if (text == null) {
                throw new IllegalArgumentException("Input string must not be null");
            }
            String s = text.trim();
            if (s.isEmpty()) {
                throw new IllegalArgumentException("Input string must not be empty");
            }

            String[] words = s.split("\\s+");
            if (words.length == 2) {
                return parseMixed(words[0], words[1], text);
            }
            if (words.length > 2) {
                throw new IllegalArgumentException("Unexpected spaces in \"" + text + "\"");
            }

            int slash = s.indexOf('/');
            if (slash >= 0) {
                if (s.indexOf('/', slash + 1) >= 0) {
                    throw new IllegalArgumentException("More than one '/' in \"" + text + "\"");
                }
                Rational top = parsePositional(s.substring(0, slash), 10);
                Rational bottom = parsePositional(s.substring(slash + 1), 10);
                if (bottom.num.signum() == 0) {
                    throw new ArithmeticException("Division by zero in \"" + text + "\"");
                }
                return top.divide(bottom);
            }
            return parsePositional(s, 10);
        }

        /**
         * Parses positional notation in the given base (2 to 36), optionally with a
         * repeating block, for example {@code parse("0.(0011)", 2)} is 1/5.
         * In base 10 an exponent is also accepted.  Fractions and mixed numbers are
         * only understood by {@link #parse(String)}.
         */
        public static Rational parse(String text, int radix) {
            return parsePositional(text, radix);
        }

        private static Rational parseMixed(String wholeText, String fractionText, String original) {
            if (hasExponent(wholeText) || hasExponent(fractionText)) {
                throw new IllegalArgumentException("Not a mixed number: \"" + original + "\"");
            }
            boolean negative = wholeText.startsWith("-");
            String wholeDigits = (wholeText.startsWith("-") || wholeText.startsWith("+"))
                    ? wholeText.substring(1) : wholeText;
            int slash = fractionText.indexOf('/');
            if (wholeDigits.isEmpty() || slash <= 0 || slash != fractionText.lastIndexOf('/')) {
                throw new IllegalArgumentException("Not a mixed number: \"" + original + "\"");
            }
            Rational whole = parsePositional(wholeDigits, 10);
            Rational top = parsePositional(fractionText.substring(0, slash), 10);
            Rational bottom = parsePositional(fractionText.substring(slash + 1), 10);
            if (whole.den.compareTo(BigInteger.ONE) != 0 || top.num.signum() < 0 || bottom.num.signum() <= 0
                    || hasSign(fractionText) || hasSign(wholeDigits)) {
                throw new IllegalArgumentException("Not a mixed number: \"" + original + "\"");
            }
            Rational value = whole.add(top.divide(bottom));
            return negative ? value.negate() : value;
        }

        private static boolean hasSign(String s) {
            return s.indexOf('-') >= 0 || s.indexOf('+') >= 0;
        }

        private static boolean hasExponent(String s) {
            return s.indexOf('e') >= 0 || s.indexOf('E') >= 0;
        }

        private static Rational parsePositional(String text, int radix) {
            if (text == null) {
                throw new IllegalArgumentException("Input string must not be null");
            }
            checkRadix(radix);
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

            BigInteger r = BigInteger.valueOf(radix);
            BigInteger intPart = BigInteger.ZERO;
            BigInteger fracPart = BigInteger.ZERO;
            BigInteger repPart = BigInteger.ZERO;
            int fracLen = 0;
            int repLen = 0;
            int digits = 0;
            int exponent = 0;
            boolean seenDot = false;
            boolean seenOpen = false;
            boolean seenClose = false;

            for (; i < s.length(); i++) {
                char c = s.charAt(i);
                if (radix == 10 && (c == 'e' || c == 'E')) {
                    if (seenOpen && !seenClose) {
                        throw new IllegalArgumentException("Unterminated repeating block in \"" + text + "\"");
                    }
                    exponent = parseExponent(s.substring(i + 1), text);
                    break;
                }
                if (seenClose) {
                    throw new IllegalArgumentException("Unexpected '" + c + "' after the repeating block in \"" + text + "\"");
                }
                if (c == '.') {
                    if (seenDot) {
                        throw new IllegalArgumentException("Multiple decimal points in \"" + text + "\"");
                    }
                    seenDot = true;
                } else if (c == '(') {
                    if (!seenDot) {
                        throw new IllegalArgumentException("A repeating block must follow a decimal point in \"" + text + "\"");
                    }
                    if (seenOpen) {
                        throw new IllegalArgumentException("More than one repeating block in \"" + text + "\"");
                    }
                    seenOpen = true;
                } else if (c == ')') {
                    if (!seenOpen) {
                        throw new IllegalArgumentException("Unmatched ')' in \"" + text + "\"");
                    }
                    if (repLen == 0) {
                        throw new IllegalArgumentException("Empty repeating block in \"" + text + "\"");
                    }
                    seenClose = true;
                } else {
                    int d = c < 128 ? Character.digit(c, radix) : -1;
                    if (d < 0) {
                        throw new IllegalArgumentException("Invalid character '" + c + "' in \"" + text + "\"");
                    }
                    BigInteger dv = BigInteger.valueOf(d);
                    digits++;
                    if (seenOpen) {
                        repPart = mul(repPart, r).add(dv);
                        repLen++;
                    } else if (seenDot) {
                        fracPart = mul(fracPart, r).add(dv);
                        fracLen++;
                    } else {
                        intPart = mul(intPart, r).add(dv);
                    }
                }
            }
            if (seenOpen && !seenClose) {
                throw new IllegalArgumentException("Unterminated repeating block in \"" + text + "\"");
            }
            if (digits == 0) {
                throw new IllegalArgumentException("No digits in \"" + text + "\"");
            }

            // value = I + A/r^a + B/(r^a (r^b - 1))
            BigInteger scaleA = powInt(r, fracLen);
            BigInteger base = mul(intPart, scaleA).add(fracPart);
            BigInteger numerator;
            BigInteger denominator;
            if (repLen == 0) {
                numerator = base;
                denominator = scaleA;
            } else {
                BigInteger nines = powInt(r, repLen).subtract(BigInteger.ONE);
                numerator = mul(base, nines).add(repPart);
                denominator = mul(scaleA, nines);
            }
            Rational value = of(negative ? numerator.negate() : numerator, denominator);
            return exponent == 0 ? value : value.scaleByPowerOfTen(exponent);
        }

        // ── arithmetic ───────────────────────────────────────────────────────

        public Rational add(Rational o) {
            return of(mul(num, o.den).add(mul(o.num, den)), mul(den, o.den));
        }

        public Rational subtract(Rational o) {
            return of(mul(num, o.den).subtract(mul(o.num, den)), mul(den, o.den));
        }

        public Rational multiply(Rational o) {
            return of(mul(num, o.num), mul(den, o.den));
        }

        /** @throws ArithmeticException if o is zero */
        public Rational divide(Rational o) {
            if (o.num.signum() == 0) {
                throw new ArithmeticException("Division by zero");
            }
            return of(mul(num, o.den), mul(den, o.num));
        }

        public Rational negate() {
            return new Rational(num.negate(), den);
        }

        public Rational abs() {
            return num.signum() < 0 ? negate() : this;
        }

        /** @throws ArithmeticException if this is zero */
        public Rational reciprocal() {
            if (num.signum() == 0) {
                throw new ArithmeticException("Division by zero");
            }
            return of(den, num);
        }

        /** This raised to an integer power, exactly (negative powers take the reciprocal). */
        public Rational pow(int exponent) {
            if (exponent == 0) {
                return ONE;
            }
            long e = exponent;
            Rational base = this;
            if (e < 0) {
                base = reciprocal();       // throws if zero
                e = -e;
            }
            return new Rational(powInt(base.num, e), powInt(base.den, e));
        }

        public int signum() {
            return num.signum();
        }

        public boolean isInteger() {
            return den.equals(BigInteger.ONE);
        }

        // ── bounded-precision arithmetic ─────────────────────────────────────

        /** this + o, then limited to the precision of {@code ctx}. */
        public Rational add(Rational o, Context ctx) {
            return ctx.apply(add(o));
        }

        /** this - o, then limited to the precision of {@code ctx}. */
        public Rational subtract(Rational o, Context ctx) {
            return ctx.apply(subtract(o));
        }

        /** this * o, then limited to the precision of {@code ctx}. */
        public Rational multiply(Rational o, Context ctx) {
            return ctx.apply(multiply(o));
        }

        /** this / o, then limited to the precision of {@code ctx}. */
        public Rational divide(Rational o, Context ctx) {
            return ctx.apply(divide(o));
        }

        /** Rounds to the given number of significant decimal digits. */
        public Rational roundToSignificantDigits(int digits, RoundingMode mode) {
            if (digits < 1) {
                throw new IllegalArgumentException("Significant digits must be at least 1");
            }
            Objects.requireNonNull(mode, "mode");
            if (num.signum() == 0) {
                return this;
            }
            long lastKept = floorLog10(abs()) - digits + 1;      // exponent of the last digit kept
            BigInteger units = scaleByPowerOfTen(-lastKept).round(mode);
            return lastKept >= 0 ? of(mul(units, tenPow(lastKept))) : of(units, tenPow(-lastKept));
        }

        /** Rounds to the given number of decimal places. */
        public Rational roundToDecimalPlaces(int places, RoundingMode mode) {
            if (places < 0) {
                throw new IllegalArgumentException("Decimal places must not be negative");
            }
            return roundToDenominator(tenPow(places), mode);
        }

        /**
         * A precision limit applied to a result, in the way {@link java.math.MathContext}
         * limits a BigDecimal.  Four kinds exist:
         * <ul>
         *   <li>{@link #UNLIMITED}: no limit; results stay exact (and operations whose
         *       exact result is not rational, such as sqrt(2), throw)</li>
         *   <li>{@link #significantDigits(int, RoundingMode)}: round to N significant decimal digits</li>
         *   <li>{@link #decimalPlaces(int, RoundingMode)}: round to N places after the decimal point</li>
         *   <li>{@link #maxDenominator(BigInteger)}: the nearest fraction whose denominator is at
         *       most the limit, which keeps numerators and denominators from growing without bound</li>
         * </ul>
         * Where a rounding mode applies, the context holds it.  The denominator limit
         * always chooses the nearest fraction, so it has no rounding mode.
         */
        public static final class Context {
            private enum Kind { UNLIMITED, SIGNIFICANT_DIGITS, DECIMAL_PLACES, MAX_DENOMINATOR }

            /** No limit: results stay exact. */
            public static final Context UNLIMITED = new Context(Kind.UNLIMITED, 0, BigInteger.ONE, RoundingMode.HALF_UP);

            private final Kind kind;
            private final int amount;
            private final BigInteger limit;
            private final RoundingMode mode;

            private Context(Kind kind, int amount, BigInteger limit, RoundingMode mode) {
                this.kind = kind;
                this.amount = amount;
                this.limit = limit;
                this.mode = mode;
            }

            /** N significant decimal digits, rounded with {@code mode}. */
            public static Context significantDigits(int digits, RoundingMode mode) {
                if (digits < 1) {
                    throw new IllegalArgumentException("Significant digits must be at least 1");
                }
                return new Context(Kind.SIGNIFICANT_DIGITS, digits, BigInteger.ONE, Objects.requireNonNull(mode, "mode"));
            }

            /** N significant decimal digits, rounded HALF_UP. */
            public static Context significantDigits(int digits) {
                return significantDigits(digits, RoundingMode.HALF_UP);
            }

            /** N places after the decimal point, rounded with {@code mode}. */
            public static Context decimalPlaces(int places, RoundingMode mode) {
                if (places < 0) {
                    throw new IllegalArgumentException("Decimal places must not be negative");
                }
                return new Context(Kind.DECIMAL_PLACES, places, BigInteger.ONE, Objects.requireNonNull(mode, "mode"));
            }

            /** N places after the decimal point, rounded HALF_UP. */
            public static Context decimalPlaces(int places) {
                return decimalPlaces(places, RoundingMode.HALF_UP);
            }

            /** Nearest fraction with a denominator of at most {@code max}. */
            public static Context maxDenominator(BigInteger max) {
                if (max.signum() <= 0) {
                    throw new IllegalArgumentException("Maximum denominator must be positive");
                }
                return new Context(Kind.MAX_DENOMINATOR, 0, max, RoundingMode.HALF_EVEN);
            }

            public static Context maxDenominator(long max) {
                return maxDenominator(BigInteger.valueOf(max));
            }

            /** Limits {@code x} to this context's precision. */
            public Rational apply(Rational x) {
                switch (kind) {
                    case SIGNIFICANT_DIGITS: return x.roundToSignificantDigits(amount, mode);
                    case DECIMAL_PLACES:     return x.roundToDecimalPlaces(amount, mode);
                    case MAX_DENOMINATOR:    return x.bestApproximation(limit);
                    default:                 return x;
                }
            }

            @Override
            public String toString() {
                switch (kind) {
                    case SIGNIFICANT_DIGITS: return "significantDigits(" + amount + ", " + mode + ")";
                    case DECIMAL_PLACES:     return "decimalPlaces(" + amount + ", " + mode + ")";
                    case MAX_DENOMINATOR:    return "maxDenominator(" + limit + ")";
                    default:                 return "UNLIMITED";
                }
            }
        }

        /** floor(log10(x)) for x &gt; 0. */
        private static long floorLog10(Rational x) {
            long t = (long) x.num.toString().length() - x.den.toString().length();
            // x lies strictly between 10^(t-1) and 10^(t+1); is it at least 10^t?
            boolean atLeast = t >= 0
                    ? x.num.compareTo(mul(tenPow(t), x.den)) >= 0
                    : mul(x.num, tenPow(-t)).compareTo(x.den) >= 0;
            return atLeast ? t : t - 1;
        }

        // ── square roots, nth roots and powers ───────────────────────────────

        /**
         * Square root, exactly.  Succeeds only if the root is rational (both numerator
         * and denominator are perfect squares); otherwise use {@link #sqrt(Context)}.
         *
         * @throws ArithmeticException if this is negative or its root is irrational
         */
        public Rational sqrt() {
            return root(2, Context.UNLIMITED);
        }

        /**
         * Square root limited to the precision of {@code ctx}, correctly rounded: the
         * result is the true root rounded as the context says, never an approximation
         * of an approximation.  An exact root (such as 3/2 for 9/4) is rounded the same
         * way.
         *
         * @throws ArithmeticException if this is negative, or if the root is irrational
         *         and {@code ctx} is {@link Context#UNLIMITED}
         */
        public Rational sqrt(Context ctx) {
            return root(2, ctx);
        }

        /** True if the nth root of this is itself a rational number. */
        public boolean hasRationalRoot(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("Root index must be at least 1");
            }
            if (num.signum() < 0 && (n & 1) == 0) {
                return false;
            }
            Rational x = abs();
            return isPerfectPower(x.num, n) && isPerfectPower(x.den, n);
        }

        /**
         * The nth root (n at least 1), limited to the precision of {@code ctx} and
         * correctly rounded.  Odd roots of negative numbers are real: the cube root of
         * -8 is -2.
         *
         * @throws ArithmeticException for an even root of a negative number, or if the
         *         root is irrational and {@code ctx} is {@link Context#UNLIMITED}
         */
        public Rational root(int n, Context ctx) {
            if (n < 1) {
                throw new IllegalArgumentException("Root index must be at least 1");
            }
            Objects.requireNonNull(ctx, "ctx");
            if (n == 1) {
                return ctx.apply(this);
            }
            if (num.signum() == 0) {
                return ZERO;
            }
            boolean negative = num.signum() < 0;
            if (negative && (n & 1) == 0) {
                throw new ArithmeticException("Even root of a negative number");
            }
            Rational x = abs();

            // A rational root exists exactly when numerator and denominator are both perfect powers
            BigInteger rootNum = integerRoot(x.num, n);
            BigInteger rootDen = integerRoot(x.den, n);
            if (powInt(rootNum, n).equals(x.num) && powInt(rootDen, n).equals(x.den)) {
                Rational exact = of(rootNum, rootDen);
                return ctx.apply(negative ? exact.negate() : exact);
            }

            // The root is irrational
            Rational magnitudeResult;
            switch (ctx.kind) {
                case SIGNIFICANT_DIGITS:
                case DECIMAL_PLACES:
                    magnitudeResult = irrationalRootToDigits(x, n, negative, ctx);
                    return negative ? magnitudeResult.negate() : magnitudeResult;
                case MAX_DENOMINATOR:
                    magnitudeResult = irrationalRootNearest(x, n, ctx.limit);
                    return negative ? magnitudeResult.negate() : magnitudeResult;
                default:
                    throw new ArithmeticException("The root is irrational; a precision limit is required");
            }
        }

        /**
         * This raised to a rational power, limited to the precision of {@code ctx}.
         * For an exponent p/q the result is the qth root of this^p, rounded once, so it
         * is correctly rounded.  Negative bases work when q is odd; a zero base needs a
         * positive exponent.
         *
         * @throws ArithmeticException for a negative base with an even root, zero to a
         *         negative power, an exponent too large to handle, or an irrational
         *         result under {@link Context#UNLIMITED}
         */
        public Rational pow(Rational exponent, Context ctx) {
            Objects.requireNonNull(exponent, "exponent");
            Objects.requireNonNull(ctx, "ctx");
            if (exponent.num.bitLength() > 30 || exponent.den.bitLength() > 30) {
                throw new ArithmeticException("Exponent too large");
            }
            int p = exponent.num.intValue();
            int q = exponent.den.intValue();
            if (q == 1) {
                return ctx.apply(pow(p));
            }
            if (num.signum() == 0) {
                if (p > 0) {
                    return ZERO;
                }
                throw new ArithmeticException("Zero raised to a negative power");
            }
            if (num.signum() < 0 && (q & 1) == 0) {
                throw new ArithmeticException("Even root of a negative number");
            }
            Rational base = p < 0 ? reciprocal() : this;
            return base.pow(Math.abs(p)).root(q, ctx);
        }

        /** This raised to a rational power, exactly; the result must be rational. */
        public Rational pow(Rational exponent) {
            return pow(exponent, Context.UNLIMITED);
        }

        /** This raised to an integer power, then limited to the precision of {@code ctx}. */
        public Rational pow(int exponent, Context ctx) {
            return ctx.apply(pow(exponent));
        }

        /** floor of the nth root of a non-negative integer, by Newton's method. */
        private static BigInteger integerRoot(BigInteger n, int k) {
            if (k == 1 || n.compareTo(BigInteger.ONE) <= 0) {
                return n;
            }
            BigInteger kBig = BigInteger.valueOf(k);
            BigInteger kMinusOne = BigInteger.valueOf(k - 1);
            BigInteger x = BigInteger.ONE.shiftLeft((n.bitLength() + k - 1) / k);   // at least the root
            while (true) {
                BigInteger y = longDivide(mul(kMinusOne, x).add(longDivide(n, powInt(x, k - 1))), kBig);
                if (y.compareTo(x) >= 0) {
                    return x;
                }
                x = y;
            }
        }

        private static boolean isPerfectPower(BigInteger n, int k) {
            return powInt(integerRoot(n, k), k).equals(n);
        }

        /**
         * Rounds the irrational nth root of x (x &gt; 0) to digits.  With
         * k = the number of decimal places kept, floor(root(x) * 10^k) is the
         * integer root of floor(x * 10^(n k)); the rounding decision compares
         * x * 10^(n k) with the exact half-way point (s + 1/2)^n, so no
         * approximation of the root is ever rounded a second time.
         */
        private static Rational irrationalRootToDigits(Rational x, int n, boolean negative, Context ctx) {
            long k;
            if (ctx.kind == Context.Kind.SIGNIFICANT_DIGITS) {
                k = ctx.amount - 1 - Math.floorDiv(floorLog10(x), (long) n);
            } else {
                k = ctx.amount;
            }
            while (true) {
                Rational scaled = x.scaleByPowerOfTen(k * n);
                BigInteger s = integerRoot(scaled.floor(), n);
                if (ctx.kind == Context.Kind.SIGNIFICANT_DIGITS && s.toString().length() < ctx.amount) {
                    k++;                    // not expected; guards against an off-by-one in the estimate
                    continue;
                }
                // Compare root(scaled) with s + 1/2:  scaled * 2^n against (2s + 1)^n
                BigInteger twoS1 = s.add(s).add(BigInteger.ONE);
                int half = scaled.multiply(of(powInt(BigInteger.valueOf(2), n))).compareTo(of(powInt(twoS1, n)));
                boolean up = roundsUpGivenHalf(ctx.mode, negative, s.testBit(0), half);
                BigInteger units = up ? s.add(BigInteger.ONE) : s;
                return k >= 0 ? of(units, tenPow(k)) : of(mul(units, tenPow(-k)));
            }
        }

        /** Sign of (p/q)^n - x, for p/q &gt;= 0 and x &gt; 0. */
        private static int comparePowerWith(BigInteger p, BigInteger q, Rational x, int n) {
            return mul(powInt(p, n), x.den).compareTo(mul(x.num, powInt(q, n)));
        }

        /**
         * The fraction with denominator at most {@code limit} nearest to the irrational
         * nth root of x (x &gt; 0).  Walks the Stern-Brocot tree using exact comparisons
         * of (p/q)^n with x, moving in whole runs found by binary search, until the two
         * bracketing fractions are Farey neighbours that no fraction of allowed size
         * separates; then picks the nearer by comparing the root with their midpoint.
         */
        private static Rational irrationalRootNearest(Rational x, int n, BigInteger limit) {
            BigInteger floorRoot = integerRoot(longDivide(x.num, x.den), n);
            BigInteger p1 = floorRoot;                          // below the root
            BigInteger q1 = BigInteger.ONE;
            BigInteger p2 = floorRoot.add(BigInteger.ONE);      // above the root
            BigInteger q2 = BigInteger.ONE;

            boolean progress = true;
            while (progress) {
                progress = false;

                // Move the lower fraction up toward the root: (p1 + k p2) / (q1 + k q2) while below it
                BigInteger lo = BigInteger.ZERO;
                BigInteger hi = longDivide(limit.subtract(q1), q2);
                while (lo.compareTo(hi) < 0) {
                    BigInteger mid = lo.add(hi).add(BigInteger.ONE).shiftRight(1);
                    if (comparePowerWith(p1.add(mul(mid, p2)), q1.add(mul(mid, q2)), x, n) < 0) {
                        lo = mid;
                    } else {
                        hi = mid.subtract(BigInteger.ONE);
                    }
                }
                if (lo.signum() > 0) {
                    p1 = p1.add(mul(lo, p2));
                    q1 = q1.add(mul(lo, q2));
                    progress = true;
                }

                // Move the upper fraction down toward the root while above it
                lo = BigInteger.ZERO;
                hi = longDivide(limit.subtract(q2), q1);
                while (lo.compareTo(hi) < 0) {
                    BigInteger mid = lo.add(hi).add(BigInteger.ONE).shiftRight(1);
                    if (comparePowerWith(p2.add(mul(mid, p1)), q2.add(mul(mid, q1)), x, n) > 0) {
                        lo = mid;
                    } else {
                        hi = mid.subtract(BigInteger.ONE);
                    }
                }
                if (lo.signum() > 0) {
                    p2 = p2.add(mul(lo, p1));
                    q2 = q2.add(mul(lo, q1));
                    progress = true;
                }
            }

            BigInteger midNum = mul(p1, q2).add(mul(p2, q1));
            BigInteger midDen = mul(BigInteger.valueOf(2), mul(q1, q2));
            return comparePowerWith(midNum, midDen, x, n) < 0 ? of(p2, q2) : of(p1, q1);
        }

        // ── exact comparison ─────────────────────────────────────────────────

        @Override
        public int compareTo(Rational o) {
            int bySign = Integer.compare(signum(), o.signum());
            if (bySign != 0) {
                return bySign;
            }
            return mul(num, o.den).compareTo(mul(o.num, den));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Rational)) {
                return false;
            }
            Rational o = (Rational) other;
            return num.equals(o.num) && den.equals(o.den);
        }

        @Override
        public int hashCode() {
            return 31 * num.hashCode() + den.hashCode();
        }

        // ── Number conversions ───────────────────────────────────────────────

        /** The integer part, narrowed like {@link BigInteger#intValue()}. */
        @Override
        public int intValue() {
            return truncate().intValue();
        }

        /** The integer part, narrowed like {@link BigInteger#longValue()}. */
        @Override
        public long longValue() {
            return truncate().longValue();
        }

        /** The nearest float (ties to even); may be infinite or zero when out of range. */
        @Override
        public float floatValue() {
            if (num.signum() == 0) {
                return 0.0f;
            }
            long[] t = binaryApproximation(24, -149);
            float magnitude = Math.scalb((float) t[0], clampExponent(t[1]));
            return num.signum() < 0 ? -magnitude : magnitude;
        }

        /** The nearest double (ties to even); may be infinite or zero when out of range. */
        @Override
        public double doubleValue() {
            if (num.signum() == 0) {
                return 0.0;
            }
            long[] t = binaryApproximation(53, -1074);
            double magnitude = Math.scalb((double) t[0], clampExponent(t[1]));
            return num.signum() < 0 ? -magnitude : magnitude;
        }

        private static int clampExponent(long e) {
            return (int) Math.max(-100_000L, Math.min(100_000L, e));
        }

        /**
         * |this| as q * 2^e with q holding at most {@code precision} bits, rounded to
         * nearest with ties to even.  {@code minGridExponent} is the exponent of the
         * smallest representable step, which handles subnormal numbers.  Returns {q, e}.
         */
        private long[] binaryApproximation(int precision, int minGridExponent) {
            BigInteger a = num.abs();
            BigInteger b = den;
            long e = (long) a.bitLength() - b.bitLength() - precision;
            BigInteger q;
            BigInteger r;
            BigInteger d;
            while (true) {
                BigInteger n2 = e >= 0 ? a : a.shiftLeft((int) -e);
                d = e >= 0 ? b.shiftLeft((int) e) : b;
                q = longDivide(n2, d);
                r = n2.subtract(mul(q, d));
                if (q.bitLength() > precision) {
                    e++;                         // one bit too many: halve the scale and retry
                    continue;
                }
                break;
            }

            if (e < minGridExponent) {
                // Subnormal range: fewer bits are available, so drop the extra low bits
                long shift = minGridExponent - e;
                if (shift > precision) {
                    return new long[] { 0, minGridExponent };      // below half the smallest step
                }
                BigInteger low = q.and(BigInteger.ONE.shiftLeft((int) shift).subtract(BigInteger.ONE));
                BigInteger half = BigInteger.ONE.shiftLeft((int) shift - 1);
                q = q.shiftRight((int) shift);
                int cmp = low.compareTo(half);
                if (cmp > 0 || (cmp == 0 && (r.signum() != 0 || q.testBit(0)))) {
                    q = q.add(BigInteger.ONE);
                }
                return new long[] { q.longValue(), minGridExponent };
            }

            int cmp = r.add(r).compareTo(d);
            if (cmp > 0 || (cmp == 0 && q.testBit(0))) {
                q = q.add(BigInteger.ONE);
            }
            return new long[] { q.longValue(), e };
        }

        // ── floor, ceiling and rounding ──────────────────────────────────────

        /** Largest integer not greater than this. */
        public BigInteger floor() {
            BigInteger q = longDivide(num.abs(), den);
            if (num.signum() >= 0) {
                return q;
            }
            boolean exact = mul(q, den).equals(num.abs());
            return exact ? q.negate() : q.add(BigInteger.ONE).negate();
        }

        /** Smallest integer not less than this. */
        public BigInteger ceil() {
            BigInteger q = longDivide(num.abs(), den);
            boolean exact = mul(q, den).equals(num.abs());
            if (num.signum() < 0) {
                return q.negate();
            }
            return exact ? q : q.add(BigInteger.ONE);
        }

        /** Integer part, rounding toward zero. */
        public BigInteger truncate() {
            BigInteger q = longDivide(num.abs(), den);
            return num.signum() < 0 ? q.negate() : q;
        }

        /** This minus its floor: always in [0, 1). */
        public Rational fractionalPart() {
            return subtract(of(floor()));
        }

        /**
         * Rounds to an integer with the given mode.
         *
         * @throws ArithmeticException if mode is UNNECESSARY and this is not an integer
         */
        public BigInteger round(RoundingMode mode) {
            Objects.requireNonNull(mode, "mode");
            boolean negative = num.signum() < 0;
            BigInteger magnitude = num.abs();
            BigInteger q = longDivide(magnitude, den);
            BigInteger rem = magnitude.subtract(mul(q, den));
            if (rem.signum() != 0 && roundsUp(mode, negative, q.testBit(0), rem, den)) {
                q = q.add(BigInteger.ONE);
            }
            return negative ? q.negate() : q;
        }

        /**
         * Rounds to the nearest multiple of 1/n, for example n = 16 rounds to the
         * nearest sixteenth.
         *
         * @throws IllegalArgumentException if n is not positive
         */
        public Rational roundToDenominator(BigInteger n, RoundingMode mode) {
            if (n.signum() <= 0) {
                throw new IllegalArgumentException("Denominator must be positive");
            }
            return of(multiply(of(n)).round(mode), n);
        }

        public Rational roundToDenominator(long n, RoundingMode mode) {
            return roundToDenominator(BigInteger.valueOf(n), mode);
        }

        // ── repeating-cycle analysis ─────────────────────────────────────────

        /** Digits before the repeating block in base 10 (the whole expansion length if it terminates). */
        public long preperiodLength() {
            return preperiodLength(10);
        }

        /**
         * Number of digits before the repeating block when written in the given
         * base.  If the expansion terminates this is its number of fractional
         * digits.  Found from the denominator's factors, without generating digits.
         */
        public long preperiodLength(int base) {
            checkRadix(base);
            return splitDenominator(den, BigInteger.valueOf(base))[1].longValue();
        }

        /** Length of the repeating block in base 10 (0 if the expansion terminates). */
        public long periodLength() {
            return periodLength(10);
        }

        /**
         * Length of the repeating block in the given base, or 0 if the expansion
         * terminates.  This is the multiplicative order of the base modulo the part
         * of the denominator coprime to the base.  It is found by stepping through
         * the remainders, so it takes as many steps as the period is long but
         * generates no digits and uses constant memory.
         */
        public long periodLength(int base) {
            checkRadix(base);
            BigInteger b = BigInteger.valueOf(base);
            BigInteger core = splitDenominator(den, b)[0];
            if (core.equals(BigInteger.ONE)) {
                return 0;
            }
            BigInteger r = modulo(b, core);
            long k = 1;
            while (!r.equals(BigInteger.ONE)) {
                r = modulo(mul(r, b), core);
                k++;
            }
            return k;
        }

        // ── expansion in any base ────────────────────────────────────────────

        /**
         * This value written in the given base (2 to 36).  With places = -1 the
         * exact expansion is produced with any repeating block in parentheses,
         * otherwise the result is rounded to that many places.
         */
        public String toRadixString(int radix, long places, RoundingMode mode) {
            return divideToString(num, den, places, mode, radix);
        }

        /** Decimal text, exact when places = -1 (for example "0.(3)"), otherwise rounded. */
        public String toDecimalString(long places, RoundingMode mode) {
            return toRadixString(10, places, mode);
        }

        /** Exact decimal text with the repeating block in parentheses, e.g. "3.(142857)". */
        public String toRepeatingString() {
            return toRadixString(10, -1, RoundingMode.HALF_UP);
        }

        // ── scientific and engineering notation ──────────────────────────────

        /**
         * Scientific notation rounded to exactly {@code significantDigits} digits,
         * trailing zeros included: 12345.678 with 5 digits is "1.2346E+4" and 1/4 with
         * 5 digits is "2.5000E-1".  Zero is "0E+0".
         */
        public String toScientificString(int significantDigits, RoundingMode mode) {
            return formatSignificant(significantDigits, mode, false);
        }

        /**
         * Engineering notation (exponent a multiple of 3) rounded to exactly
         * {@code significantDigits} digits: 12345.678 with 5 digits is "12.346E+3".
         */
        public String toEngineeringString(int significantDigits, RoundingMode mode) {
            return formatSignificant(significantDigits, mode, true);
        }

        /**
         * Scientific notation with the fewest digits that represent the value exactly,
         * for example "1.25E-1".
         *
         * @throws ArithmeticException if the decimal expansion does not terminate
         *         (use the digit-count version for those values)
         */
        public String toScientificString() {
            return formatExact(false);
        }

        /** Engineering notation with the fewest digits that represent the value exactly. */
        public String toEngineeringString() {
            return formatExact(true);
        }

        private String formatSignificant(int digits, RoundingMode mode, boolean engineering) {
            if (digits < 1) {
                throw new IllegalArgumentException("Significant digits must be at least 1");
            }
            Objects.requireNonNull(mode, "mode");
            if (num.signum() == 0) {
                return "0E+0";
            }
            long exponent = floorLog10(abs());
            BigInteger units = scaleByPowerOfTen(digits - 1 - exponent).round(mode);
            boolean negative = units.signum() < 0;
            String ds = units.abs().toString();
            if (ds.length() > digits) {          // rounded up to the next power of ten
                ds = ds.substring(0, digits);
                exponent++;
            }
            return formatScientific(negative ? "-" : "", ds, exponent, engineering);
        }

        private String formatExact(boolean engineering) {
            if (num.signum() == 0) {
                return "0E+0";
            }
            if (!splitDenominator(den, TEN)[0].equals(BigInteger.ONE)) {
                throw new ArithmeticException("Non-terminating decimal expansion; specify a number of digits");
            }
            long fractionDigits = preperiodLength();
            String full = quotient(mul(num.abs(), tenPow(fractionDigits)), den).toString();
            return formatScientific(num.signum() < 0 ? "-" : "", stripTrailingZeros(full),
                                    (long) full.length() - 1 - fractionDigits, engineering);
        }

        // ── continued fractions and approximation ────────────────────────────

        /**
         * The continued-fraction terms [a0; a1, a2, ...]: a0 is the floor and every
         * later term is a positive integer.  The expansion of a rational is finite.
         */
        public List<BigInteger> continuedFraction() {
            List<BigInteger> terms = new ArrayList<>();
            BigInteger a0 = floor();
            terms.add(a0);
            Rational rest = subtract(of(a0));
            while (rest.num.signum() != 0) {
                Rational inverse = rest.reciprocal();
                BigInteger a = inverse.floor();
                terms.add(a);
                rest = inverse.subtract(of(a));
            }
            return terms;
        }

        /** Text form of {@link #continuedFraction()}, e.g. "[3; 7, 16]". */
        public String toContinuedFractionString() {
            List<BigInteger> terms = continuedFraction();
            StringBuilder sb = new StringBuilder("[").append(terms.get(0));
            for (int i = 1; i < terms.size(); i++) {
                sb.append(i == 1 ? "; " : ", ").append(terms.get(i));
            }
            return sb.append(']').toString();
        }

        /** The successive best rational approximations: truncations of the continued fraction. */
        public List<Rational> convergents() {
            List<Rational> result = new ArrayList<>();
            BigInteger hPrev2 = BigInteger.ZERO;
            BigInteger hPrev1 = BigInteger.ONE;
            BigInteger kPrev2 = BigInteger.ONE;
            BigInteger kPrev1 = BigInteger.ZERO;
            for (BigInteger a : continuedFraction()) {
                BigInteger h = mul(a, hPrev1).add(hPrev2);
                BigInteger k = mul(a, kPrev1).add(kPrev2);
                result.add(of(h, k));
                hPrev2 = hPrev1;
                hPrev1 = h;
                kPrev2 = kPrev1;
                kPrev1 = k;
            }
            return result;
        }

        /**
         * Builds a rational from continued-fraction terms.  The first term may be
         * any integer; the rest must be positive.
         */
        public static Rational fromContinuedFraction(List<BigInteger> terms) {
            if (terms == null || terms.isEmpty()) {
                throw new IllegalArgumentException("At least one term is required");
            }
            for (int i = 1; i < terms.size(); i++) {
                if (terms.get(i).signum() <= 0) {
                    throw new IllegalArgumentException("Terms after the first must be positive");
                }
            }
            Rational x = of(terms.get(terms.size() - 1));
            for (int i = terms.size() - 2; i >= 0; i--) {
                x = of(terms.get(i)).add(x.reciprocal());
            }
            return x;
        }

        public static Rational fromContinuedFraction(long... terms) {
            List<BigInteger> list = new ArrayList<>();
            for (long t : terms) {
                list.add(BigInteger.valueOf(t));
            }
            return fromContinuedFraction(list);
        }

        /**
         * The closest rational to this whose denominator is at most
         * {@code maxDenominator}.  For example pi to many digits with a limit of
         * 1000 gives 355/113.
         *
         * @throws IllegalArgumentException if maxDenominator is not positive
         */
        public Rational bestApproximation(BigInteger maxDenominator) {
            if (maxDenominator.signum() <= 0) {
                throw new IllegalArgumentException("Maximum denominator must be positive");
            }
            if (den.compareTo(maxDenominator) <= 0) {
                return this;
            }
            if (num.signum() < 0) {
                return negate().bestApproximation(maxDenominator).negate();
            }

            // Walk the convergents until the next one would exceed the limit
            BigInteger p0 = BigInteger.ZERO, q0 = BigInteger.ONE;
            BigInteger p1 = BigInteger.ONE,  q1 = BigInteger.ZERO;
            BigInteger n = num;
            BigInteger d = den;
            while (true) {
                BigInteger a = longDivide(n, d);
                BigInteger q2 = q0.add(mul(a, q1));
                if (q2.compareTo(maxDenominator) > 0) {
                    break;
                }
                BigInteger p2 = p0.add(mul(a, p1));
                p0 = p1; q0 = q1; p1 = p2; q1 = q2;
                BigInteger nextD = n.subtract(mul(a, d));
                n = d;
                d = nextD;
            }

            // The best candidates are the last convergent and a semiconvergent
            BigInteger k = longDivide(maxDenominator.subtract(q0), q1);
            Rational bound1 = of(p0.add(mul(k, p1)), q0.add(mul(k, q1)));
            Rational bound2 = of(p1, q1);
            return bound2.subtract(this).abs().compareTo(bound1.subtract(this).abs()) <= 0 ? bound2 : bound1;
        }

        public Rational bestApproximation(long maxDenominator) {
            return bestApproximation(BigInteger.valueOf(maxDenominator));
        }

        /**
         * The simplest rational (smallest denominator) within {@code tolerance} of
         * {@code x}, endpoints included.  Useful for recovering a clean fraction
         * from a rounded value.
         *
         * @throws IllegalArgumentException if tolerance is negative
         */
        public static Rational approximate(Rational x, Rational tolerance) {
            if (tolerance.signum() < 0) {
                throw new IllegalArgumentException("Tolerance must not be negative");
            }
            return simplestBetween(x.subtract(tolerance), x.add(tolerance));
        }

        /**
         * Recovers the simplest fraction consistent with a decimal string, treating
         * the string as rounded to its last digit: "0.142857" gives 1/7 and
         * "0.3333" gives 1/3.  Use {@link #fromDecimal(String, Rational)} to set
         * your own tolerance.
         */
        public static Rational fromDecimal(String text) {
            Decimal d = parseDecimal(text);
            Rational halfUnit = of(BigInteger.valueOf(5), pow10(d.scale + 1));
            return approximate(of(d), halfUnit);
        }

        public static Rational fromDecimal(String text, Rational tolerance) {
            return approximate(of(parseDecimal(text)), tolerance);
        }

        private static Rational simplestBetween(Rational lo, Rational hi) {
            if (lo.signum() <= 0 && hi.signum() >= 0) {
                return ZERO;
            }
            if (hi.signum() < 0) {
                return simplestBetween(hi.negate(), lo.negate()).negate();
            }
            // 0 < lo <= hi
            BigInteger f = lo.floor();
            Rational whole = of(f);
            if (whole.equals(lo)) {
                return lo;
            }
            if (f.compareTo(hi.floor()) < 0) {
                return of(f.add(BigInteger.ONE));
            }
            Rational inner = simplestBetween(hi.subtract(whole).reciprocal(), lo.subtract(whole).reciprocal());
            return whole.add(inner.reciprocal());
        }

        // ── formatting ───────────────────────────────────────────────────────

        /** "22/7", or just "3" for an integer. */
        @Override
        public String toString() {
            return isInteger() ? num.toString() : num + "/" + den;
        }

        /** "3 1/7", "-3 1/7", "1/7" or "3". */
        public String toMixedString() {
            if (isInteger()) {
                return num.toString();
            }
            BigInteger magnitude = num.abs();
            BigInteger whole = longDivide(magnitude, den);
            BigInteger rem = magnitude.subtract(mul(whole, den));
            String sign = num.signum() < 0 ? "-" : "";
            return whole.signum() == 0
                    ? sign + rem + "/" + den
                    : sign + whole + " " + rem + "/" + den;
        }

        /**
         * Splits a positive value into an integer part (omitted if zero) followed by
         * distinct unit fractions that sum to the fractional part, found greedily:
         * 4/13 = 1/4 + 1/18 + 1/468.  The denominators can grow very quickly.
         *
         * @throws IllegalArgumentException unless this is positive
         */
        public List<Rational> egyptianFractions() {
            if (num.signum() <= 0) {
                throw new IllegalArgumentException("Egyptian fractions need a positive value");
            }
            List<Rational> terms = new ArrayList<>();
            BigInteger whole = floor();
            Rational x = this;
            if (whole.signum() > 0) {
                terms.add(of(whole));
                x = x.subtract(of(whole));
            }
            while (x.num.signum() != 0) {
                BigInteger d = x.reciprocal().ceil();
                Rational unit = of(BigInteger.ONE, d);
                terms.add(unit);
                x = x.subtract(unit);
            }
            return Collections.unmodifiableList(terms);
        }

        /** Text form of {@link #egyptianFractions()}, e.g. "1/4 + 1/18 + 1/468". */
        public String toEgyptianString() {
            StringBuilder sb = new StringBuilder();
            for (Rational term : egyptianFractions()) {
                if (sb.length() > 0) {
                    sb.append(" + ");
                }
                sb.append(term);
            }
            return sb.toString();
        }
    }

    // ── Interactive Program Entry Point ──────────────────────────────────────

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== BigFixedArithmetic Demo ===");
        System.out.println("Numbers may be integers, decimals or scientific notation, e.g. 42, -0.5, 3.14159, 1.5e3");

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

            Rational quotient = Rational.of(a).divide(Rational.of(b));
            System.out.println("\n--- Exact Fraction Forms of a / b ---");
            System.out.println("Fraction           : " + quotient);
            System.out.println("Mixed number       : " + quotient.toMixedString());
            System.out.println("Continued fraction : " + quotient.toContinuedFractionString());
            System.out.println("Scientific (10 sig): " + quotient.toScientificString(10, RoundingMode.HALF_UP));
            if (quotient.signum() >= 0) {
                Rational root = quotient.sqrt(Rational.Context.significantDigits(30));
                System.out.println("Square root (30 sig): " + root.toScientificString());
            }
        } catch (ArithmeticException | IllegalArgumentException e) {
            System.out.println();
            System.out.println("Error: " + e.getMessage());
        }

        scanner.close();
    }
}

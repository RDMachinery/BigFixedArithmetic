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
 *
 * <h2>{@link NumberTheory}</h2>
 * Extended gcd, modular inverses, the Chinese remainder theorem, primality and
 * factorisation, arithmetic functions, multiplicative order and primitive roots,
 * Farey sequences, Stern-Brocot paths, Pell equations, and exact Bernoulli
 * numbers, harmonic numbers and sums of powers.
 *
 * <h2>{@link Finance}</h2>
 * Allocation of money in proportion to weights so the shares add back to
 * exactly the original amount.
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

    // ── Number theory ────────────────────────────────────────────────────────

    /**
     * Number-theory tools: extended gcd, modular inverses, the Chinese
     * remainder theorem, primality and factorisation, arithmetic functions
     * (phi, mu, sigma, tau, lambda), multiplicative order and primitive roots,
     * Farey sequences and Stern-Brocot paths, Pell equations, and exact
     * Bernoulli numbers, harmonic numbers and sums of powers.
     *
     * <p>Unlike the long-hand routines above, this class uses BigInteger's own
     * multiply, divide, mod and gcd, because factorisation and modular
     * exponentiation are only practical with fast arithmetic.  Results that are
     * fractions are returned as {@link Rational}.
     */
    public static final class NumberTheory {
        private static final BigInteger Z = BigInteger.ZERO;
        private static final BigInteger O = BigInteger.ONE;
        private static final BigInteger TWO = BigInteger.valueOf(2);

        private NumberTheory() {
        }

        private static BigInteger big(long v) {
            return BigInteger.valueOf(v);
        }

        private static void requirePositive(BigInteger n, String what) {
            if (n.signum() <= 0) {
                throw new ArithmeticException(what + " must be positive");
            }
        }

        // ── gcd family ───────────────────────────────────────────────────────

        /** Result of {@link #extendedGcd}: g = gcd(a, b) = a*x + b*y, with g >= 0. */
        public static final class Bezout {
            public final BigInteger g;
            public final BigInteger x;
            public final BigInteger y;

            Bezout(BigInteger g, BigInteger x, BigInteger y) {
                this.g = g;
                this.x = x;
                this.y = y;
            }

            @Override
            public String toString() {
                return "gcd=" + g + ", x=" + x + ", y=" + y;
            }
        }

        /**
         * Extended Euclidean algorithm.  Returns g = gcd(a, b) >= 0 and
         * coefficients x, y with a*x + b*y = g.  The coefficients are the
         * minimal ones the algorithm produces, so |x| &lt;= |b|/(2g) and
         * |y| &lt;= |a|/(2g) when both are non-zero.
         */
        public static Bezout extendedGcd(BigInteger a, BigInteger b) {
            BigInteger oldR = a, r = b;
            BigInteger oldS = O, s = Z;
            BigInteger oldT = Z, t = O;
            while (r.signum() != 0) {
                BigInteger[] qr = oldR.divideAndRemainder(r);
                BigInteger q = qr[0];
                BigInteger tmp = oldR.subtract(q.multiply(r));
                oldR = r;
                r = tmp;
                tmp = oldS.subtract(q.multiply(s));
                oldS = s;
                s = tmp;
                tmp = oldT.subtract(q.multiply(t));
                oldT = t;
                t = tmp;
            }
            if (oldR.signum() < 0) {
                oldR = oldR.negate();
                oldS = oldS.negate();
                oldT = oldT.negate();
            }
            return new Bezout(oldR, oldS, oldT);
        }

        /**
         * The inverse of a modulo m, for any modulus m &gt;= 1 (not only primes),
         * in the range [0, m).
         *
         * @throws ArithmeticException if gcd(a, m) is not 1
         */
        public static BigInteger modInverse(BigInteger a, BigInteger m) {
            requirePositive(m, "Modulus");
            Bezout e = extendedGcd(a.mod(m), m);
            if (!e.g.equals(O)) {
                throw new ArithmeticException(a + " has no inverse modulo " + m);
            }
            return e.x.mod(m);
        }

        /** Greatest common divisor of rationals: gcd(numerators) / lcm(denominators). */
        public static Rational gcd(Rational a, Rational b) {
            BigInteger g = a.num.gcd(b.num);
            BigInteger l = lcm(a.den, b.den);
            return Rational.of(g, l);
        }

        /** Least common multiple of rationals: lcm(numerators) / gcd(denominators). */
        public static Rational lcm(Rational a, Rational b) {
            if (a.num.signum() == 0 || b.num.signum() == 0) {
                return Rational.ZERO;
            }
            return Rational.of(lcm(a.num, b.num), a.den.gcd(b.den));
        }

        /** gcd of any number of rationals (zero for none). */
        public static Rational gcd(Rational... values) {
            Rational g = Rational.ZERO;
            for (Rational v : values) {
                g = gcd(g, v);
            }
            return g;
        }

        /** lcm of any number of rationals (one for none). */
        public static Rational lcm(Rational... values) {
            Rational l = Rational.ONE;
            for (Rational v : values) {
                l = lcm(l, v);
            }
            return l;
        }

        /** Non-negative least common multiple of two integers (0 if either is 0). */
        public static BigInteger lcm(BigInteger a, BigInteger b) {
            if (a.signum() == 0 || b.signum() == 0) {
                return Z;
            }
            return a.divide(a.gcd(b)).multiply(b).abs();
        }

        // ── Chinese remainder theorem ────────────────────────────────────────

        /** A congruence x = residue (mod modulus), with 0 &lt;= residue &lt; modulus. */
        public static final class Congruence {
            public final BigInteger residue;
            public final BigInteger modulus;

            Congruence(BigInteger residue, BigInteger modulus) {
                this.residue = residue;
                this.modulus = modulus;
            }

            @Override
            public String toString() {
                return "x = " + residue + " (mod " + modulus + ")";
            }
        }

        /**
         * Solves the system x = residues[i] (mod moduli[i]).  The moduli need not
         * be coprime.  The result is the unique solution modulo lcm(moduli).
         *
         * @throws ArithmeticException if the system has no solution, a modulus is
         *         not positive, or the arrays differ in length or are empty
         */
        public static Congruence crt(BigInteger[] residues, BigInteger[] moduli) {
            if (residues.length != moduli.length || residues.length == 0) {
                throw new ArithmeticException("Need the same non-zero number of residues and moduli");
            }
            BigInteger r1 = Z;
            BigInteger m1 = O;
            for (int i = 0; i < residues.length; i++) {
                requirePositive(moduli[i], "Modulus");
                BigInteger r2 = residues[i].mod(moduli[i]);
                BigInteger m2 = moduli[i];
                Bezout e = extendedGcd(m1, m2);
                BigInteger diff = r2.subtract(r1);
                if (diff.mod(e.g).signum() != 0) {
                    throw new ArithmeticException("The congruences are inconsistent");
                }
                BigInteger m2g = m2.divide(e.g);
                BigInteger k = diff.divide(e.g).multiply(e.x).mod(m2g);
                BigInteger lcm = m1.multiply(m2g);
                r1 = r1.add(m1.multiply(k)).mod(lcm);
                m1 = lcm;
            }
            return new Congruence(r1, m1);
        }

        // ── primality and factorisation ──────────────────────────────────────

        private static final int[] SMALL_PRIMES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41};

        /**
         * Primality test.  Exact for n below about 3.3e24 (Miller-Rabin with the
         * first 13 prime bases is a proof there); above that it is a strong
         * probable-prime test with 64 rounds, whose error is negligible.
         */
        public static boolean isPrime(BigInteger n) {
            if (n.compareTo(TWO) < 0) {
                return false;
            }
            for (int p : SMALL_PRIMES) {
                BigInteger bp = big(p);
                if (n.equals(bp)) {
                    return true;
                }
                if (n.mod(bp).signum() == 0) {
                    return false;
                }
            }
            if (n.bitLength() > 81) {
                return n.isProbablePrime(64);
            }
            BigInteger nm1 = n.subtract(O);
            int s = nm1.getLowestSetBit();
            BigInteger d = nm1.shiftRight(s);
            for (int p : SMALL_PRIMES) {
                BigInteger x = big(p).modPow(d, n);
                if (x.equals(O) || x.equals(nm1)) {
                    continue;
                }
                boolean composite = true;
                for (int i = 1; i < s; i++) {
                    x = x.multiply(x).mod(n);
                    if (x.equals(nm1)) {
                        composite = false;
                        break;
                    }
                }
                if (composite) {
                    return false;
                }
            }
            return true;
        }

        /** Smallest prime strictly greater than n. */
        public static BigInteger nextPrime(BigInteger n) {
            BigInteger c = n.compareTo(TWO) < 0 ? TWO : n.add(O);
            while (!isPrime(c)) {
                c = c.add(O);
            }
            return c;
        }

        private static BigInteger pollardBrent(BigInteger n) {
            if (!n.testBit(0)) {
                return TWO;
            }
            for (long cc = 1; ; cc++) {
                BigInteger c = big(cc);
                BigInteger y = TWO, x = TWO, ys = TWO, q = O, g = O;
                int r = 1;
                final int m = 128;
                do {
                    x = y;
                    for (int i = 0; i < r; i++) {
                        y = y.multiply(y).add(c).mod(n);
                    }
                    int k = 0;
                    do {
                        ys = y;
                        int lim = Math.min(m, r - k);
                        for (int i = 0; i < lim; i++) {
                            y = y.multiply(y).add(c).mod(n);
                            q = q.multiply(x.subtract(y).abs()).mod(n);
                        }
                        g = q.gcd(n);
                        k += m;
                    } while (k < r && g.equals(O));
                    r <<= 1;
                } while (g.equals(O));
                if (g.equals(n)) {
                    do {
                        ys = ys.multiply(ys).add(c).mod(n);
                        g = x.subtract(ys).abs().gcd(n);
                    } while (g.equals(O));
                }
                if (!g.equals(n)) {
                    return g;
                }
            }
        }

        private static void factorInto(BigInteger n, java.util.Map<BigInteger, Integer> out) {
            if (n.equals(O)) {
                return;
            }
            if (isPrime(n)) {
                out.merge(n, 1, Integer::sum);
                return;
            }
            BigInteger root = n.sqrt();
            if (root.multiply(root).equals(n)) {
                java.util.Map<BigInteger, Integer> inner = new java.util.TreeMap<>();
                factorInto(root, inner);
                for (java.util.Map.Entry<BigInteger, Integer> e : inner.entrySet()) {
                    out.merge(e.getKey(), 2 * e.getValue(), Integer::sum);
                }
                return;
            }
            BigInteger d = pollardBrent(n);
            factorInto(d, out);
            factorInto(n.divide(d), out);
        }

        /**
         * Prime factorisation of n >= 1 as a map prime to exponent, in increasing
         * prime order (empty for 1).  Uses trial division by small primes, then
         * Pollard-Brent rho with a primality test.  Numbers with two large prime
         * factors (17 digits or more each) can take minutes to hours.
         *
         * @throws ArithmeticException if n is not positive
         */
        public static java.util.Map<BigInteger, Integer> factorize(BigInteger n) {
            requirePositive(n, "Number to factor");
            java.util.Map<BigInteger, Integer> out = new java.util.TreeMap<>();
            BigInteger rest = n;
            for (int p = 2; p < 1000 && rest.bitLength() > 1; p++) {
                BigInteger bp = big(p);
                while (rest.mod(bp).signum() == 0) {
                    out.merge(bp, 1, Integer::sum);
                    rest = rest.divide(bp);
                }
            }
            factorInto(rest, out);
            return out;
        }

        /** Formats a factorisation as "2^3 * 3 * 5^2". */
        public static String factorizationString(BigInteger n) {
            if (n.equals(O)) {
                return "1";
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<BigInteger, Integer> e : factorize(n).entrySet()) {
                if (sb.length() > 0) {
                    sb.append(" * ");
                }
                sb.append(e.getKey());
                if (e.getValue() > 1) {
                    sb.append('^').append(e.getValue());
                }
            }
            return sb.toString();
        }

        // ── arithmetic functions ─────────────────────────────────────────────

        /** Euler's totient: the count of 1 &lt;= k &lt;= n with gcd(k, n) = 1. */
        public static BigInteger eulerPhi(BigInteger n) {
            BigInteger result = O;
            for (java.util.Map.Entry<BigInteger, Integer> e : factorize(n).entrySet()) {
                BigInteger p = e.getKey();
                result = result.multiply(p.subtract(O)).multiply(p.pow(e.getValue() - 1));
            }
            return result;
        }

        /** Number of positive divisors of n. */
        public static BigInteger divisorCount(BigInteger n) {
            BigInteger result = O;
            for (int e : factorize(n).values()) {
                result = result.multiply(big(e + 1L));
            }
            return result;
        }

        /** Sum of the k-th powers of the positive divisors of n (k = 1 gives sigma(n)). */
        public static BigInteger divisorSum(BigInteger n, int k) {
            if (k < 0) {
                throw new IllegalArgumentException("k must be non-negative");
            }
            BigInteger result = O;
            for (java.util.Map.Entry<BigInteger, Integer> e : factorize(n).entrySet()) {
                BigInteger pk = e.getKey().pow(k);
                BigInteger term = O;
                BigInteger power = O;
                for (int i = 1; i <= e.getValue(); i++) {
                    power = power.multiply(pk);
                    term = term.add(power);
                }
                result = result.multiply(term);
            }
            return result;
        }

        /** The Moebius function: 0 if n has a squared factor, else (-1)^(number of primes). */
        public static int mobius(BigInteger n) {
            int sign = 1;
            for (int e : factorize(n).values()) {
                if (e > 1) {
                    return 0;
                }
                sign = -sign;
            }
            return sign;
        }

        /** Product of the distinct primes of n. */
        public static BigInteger radical(BigInteger n) {
            BigInteger result = O;
            for (BigInteger p : factorize(n).keySet()) {
                result = result.multiply(p);
            }
            return result;
        }

        /** All positive divisors of n in increasing order. */
        public static List<BigInteger> divisors(BigInteger n) {
            List<BigInteger> result = new ArrayList<>();
            result.add(O);
            for (java.util.Map.Entry<BigInteger, Integer> e : factorize(n).entrySet()) {
                int size = result.size();
                BigInteger power = O;
                for (int i = 1; i <= e.getValue(); i++) {
                    power = power.multiply(e.getKey());
                    for (int j = 0; j < size; j++) {
                        result.add(result.get(j).multiply(power));
                    }
                }
            }
            Collections.sort(result);
            return result;
        }

        private static void mergeMax(java.util.Map<BigInteger, Integer> into, java.util.Map<BigInteger, Integer> from) {
            for (java.util.Map.Entry<BigInteger, Integer> e : from.entrySet()) {
                into.merge(e.getKey(), e.getValue(), Math::max);
            }
        }

        /** Factorisation of Carmichael's lambda(n), the exponent of the group of units mod n. */
        private static java.util.Map<BigInteger, Integer> lambdaFactors(BigInteger n) {
            java.util.Map<BigInteger, Integer> lam = new java.util.TreeMap<>();
            for (java.util.Map.Entry<BigInteger, Integer> e : factorize(n).entrySet()) {
                BigInteger p = e.getKey();
                int k = e.getValue();
                if (p.equals(TWO)) {
                    if (k == 2) {
                        mergeMax(lam, java.util.Collections.singletonMap(TWO, 1));
                    } else if (k >= 3) {
                        mergeMax(lam, java.util.Collections.singletonMap(TWO, k - 2));
                    }
                } else {
                    mergeMax(lam, factorize(p.subtract(O)));
                    if (k > 1) {
                        mergeMax(lam, java.util.Collections.singletonMap(p, k - 1));
                    }
                }
            }
            return lam;
        }

        /** Carmichael's lambda: the smallest L with a^L = 1 (mod n) for all a coprime to n. */
        public static BigInteger carmichaelLambda(BigInteger n) {
            BigInteger result = O;
            for (java.util.Map.Entry<BigInteger, Integer> e : lambdaFactors(n).entrySet()) {
                result = result.multiply(e.getKey().pow(e.getValue()));
            }
            return result;
        }

        // ── orders and primitive roots ───────────────────────────────────────

        /**
         * The multiplicative order of a modulo m: the least k &gt;= 1 with
         * a^k = 1 (mod m).  The order of any a modulo 1 is 1.
         *
         * @throws ArithmeticException if gcd(a, m) is not 1
         */
        public static BigInteger multiplicativeOrder(BigInteger a, BigInteger m) {
            requirePositive(m, "Modulus");
            if (m.equals(O)) {
                return O;
            }
            if (!a.gcd(m).equals(O)) {
                throw new ArithmeticException(a + " is not coprime to " + m);
            }
            java.util.Map<BigInteger, Integer> lam = lambdaFactors(m);
            BigInteger order = O;
            for (java.util.Map.Entry<BigInteger, Integer> e : lam.entrySet()) {
                order = order.multiply(e.getKey().pow(e.getValue()));
            }
            for (BigInteger q : lam.keySet()) {
                while (order.mod(q).signum() == 0 && a.modPow(order.divide(q), m).equals(O)) {
                    order = order.divide(q);
                }
            }
            return order;
        }

        /**
         * The smallest primitive root of the prime p: a generator of the units
         * modulo p.
         *
         * @throws ArithmeticException if p is not prime
         */
        public static BigInteger primitiveRoot(BigInteger p) {
            if (!isPrime(p)) {
                throw new ArithmeticException(p + " is not prime");
            }
            if (p.equals(TWO)) {
                return O;
            }
            BigInteger phi = p.subtract(O);
            Iterable<BigInteger> primes = factorize(phi).keySet();
            for (BigInteger g = TWO; ; g = g.add(O)) {
                boolean ok = true;
                for (BigInteger q : primes) {
                    if (g.modPow(phi.divide(q), p).equals(O)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return g;
                }
            }
        }

        // ── Farey sequences and the Stern-Brocot tree ────────────────────────

        /** The mediant (a.num + b.num) / (a.den + b.den), which lies between a and b. */
        public static Rational mediant(Rational a, Rational b) {
            return Rational.of(a.num.add(b.num), a.den.add(b.den));
        }

        /** True when a and b are neighbours in some Farey sequence: |a.num*b.den - b.num*a.den| = 1. */
        public static boolean areFareyNeighbours(Rational a, Rational b) {
            return a.num.multiply(b.den).subtract(b.num.multiply(a.den)).abs().equals(O);
        }

        /**
         * The Farey sequence F_n: every fraction in [0, 1] in lowest terms with
         * denominator at most n, in increasing order.  It has about 3n^2/pi^2
         * terms, so n is limited to 20000.
         */
        public static List<Rational> fareySequence(int n) {
            if (n < 1 || n > 20000) {
                throw new IllegalArgumentException("n must be from 1 to 20000");
            }
            List<Rational> out = new ArrayList<>();
            long a = 0, b = 1, c = 1, d = n;
            out.add(Rational.of(a, b));
            while (c <= n) {
                long k = (n + b) / d;
                long na = c, nb = d;
                c = k * c - a;
                d = k * d - b;
                a = na;
                b = nb;
                out.add(Rational.of(a, b));
                if (a == 1 && b == 1) {
                    break;
                }
            }
            return out;
        }

        /**
         * The two neighbours of r in the Farey sequence of order n, extended to
         * all rationals: the largest fraction strictly below r and the smallest
         * strictly above r whose denominators are at most n.  Returns
         * {below, above}.
         */
        public static Rational[] fareyNeighbours(Rational r, long n) {
            if (n < 1) {
                throw new IllegalArgumentException("n must be at least 1");
            }
            BigInteger bn = big(n);
            BigInteger p = r.num;
            BigInteger q = r.den;
            if (q.compareTo(bn) <= 0) {
                // r itself is in F_n: solve p*b - a*q = 1 and c*q - p*d = 1 with b, d <= n as large as possible.
                BigInteger inv = q.equals(O) ? Z : p.mod(q).modInverse(q);
                BigInteger b = inv.add(q.multiply(bn.subtract(inv).divide(q)));
                BigInteger a = p.multiply(b).subtract(O).divide(q);
                BigInteger d0 = q.subtract(inv).mod(q);
                BigInteger d = d0.add(q.multiply(bn.subtract(d0).divide(q)));
                BigInteger c = O.add(p.multiply(d)).divide(q);
                return new Rational[] {Rational.of(a, b), Rational.of(c, d)};
            }
            BigInteger fl = r.floor();
            BigInteger loN = fl, loD = O, hiN = fl.add(O), hiD = O;
            while (true) {
                boolean changed = false;
                BigInteger bb = p.multiply(loD).subtract(loN.multiply(q));
                BigInteger aa = hiN.multiply(q).subtract(p.multiply(hiD));
                BigInteger k = bb.subtract(O).divide(aa).min(bn.subtract(loD).divide(hiD));
                if (k.signum() > 0) {
                    loN = loN.add(k.multiply(hiN));
                    loD = loD.add(k.multiply(hiD));
                    changed = true;
                }
                bb = p.multiply(loD).subtract(loN.multiply(q));
                aa = hiN.multiply(q).subtract(p.multiply(hiD));
                k = aa.subtract(O).divide(bb).min(bn.subtract(hiD).divide(loD));
                if (k.signum() > 0) {
                    hiN = hiN.add(k.multiply(loN));
                    hiD = hiD.add(k.multiply(loD));
                    changed = true;
                }
                if (!changed) {
                    break;
                }
            }
            return new Rational[] {Rational.of(loN, loD), Rational.of(hiN, hiD)};
        }

        /** Longest path string {@link #sternBrocotPath} will build. */
        private static final long MAX_PATH = 10_000_000L;

        /**
         * The path from the root 1/1 of the Stern-Brocot tree to the positive
         * rational r, as a string of 'L' (go to the smaller side) and 'R'
         * (the larger side).  The root is the empty string; 3/2 is "RL".
         *
         * @throws ArithmeticException if r is not positive, or the path would
         *         be longer than ten million steps (for example 10000000000/1)
         */
        public static String sternBrocotPath(Rational r) {
            if (r.signum() <= 0) {
                throw new ArithmeticException("Only positive rationals are in the Stern-Brocot tree");
            }
            BigInteger a = r.num, b = r.den;
            StringBuilder sb = new StringBuilder();
            while (!a.equals(b)) {
                boolean right = a.compareTo(b) > 0;
                BigInteger big = right ? a : b;
                BigInteger small = right ? b : a;
                BigInteger k = big.subtract(O).divide(small);
                if (k.add(big(sb.length())).compareTo(big(MAX_PATH)) > 0) {
                    throw new ArithmeticException("Stern-Brocot path is too long");
                }
                int count = k.intValue();
                char ch = right ? 'R' : 'L';
                for (int i = 0; i < count; i++) {
                    sb.append(ch);
                }
                if (right) {
                    a = a.subtract(k.multiply(b));
                } else {
                    b = b.subtract(k.multiply(a));
                }
            }
            return sb.toString();
        }

        /** The rational at the end of a Stern-Brocot path of 'L' and 'R' characters (inverse of {@link #sternBrocotPath}). */
        public static Rational fromSternBrocotPath(String path) {
            // Keep the bracketing fractions lo = ln/ld and hi = hn/hd; start with 0/1 and 1/0.
            BigInteger ln = Z, ld = O, hn = O, hd = Z;
            BigInteger mn = O, md = O;
            for (int i = 0; i < path.length(); i++) {
                char ch = path.charAt(i);
                if (ch == 'L') {
                    hn = mn;
                    hd = md;
                } else if (ch == 'R') {
                    ln = mn;
                    ld = md;
                } else {
                    throw new IllegalArgumentException("Path may contain only L and R");
                }
                mn = ln.add(hn);
                md = ld.add(hd);
            }
            return Rational.of(mn, md);
        }

        // ── Pell equations ───────────────────────────────────────────────────

        /**
         * The continued fraction of sqrt(n) for a non-square n: element 0 is the
         * integer part and the rest is the repeating period.
         */
        public static List<BigInteger> sqrtContinuedFraction(BigInteger n) {
            requirePositive(n, "n");
            BigInteger a0 = n.sqrt();
            if (a0.multiply(a0).equals(n)) {
                throw new ArithmeticException(n + " is a perfect square");
            }
            List<BigInteger> out = new ArrayList<>();
            out.add(a0);
            BigInteger m = Z, d = O, a = a0;
            while (!a.equals(a0.shiftLeft(1))) {
                m = d.multiply(a).subtract(m);
                d = n.subtract(m.multiply(m)).divide(d);
                a = a0.add(m).divide(d);
                out.add(a);
            }
            return out;
        }

        /**
         * The fundamental solution {x, y} (smallest positive y) of the Pell
         * equation x^2 - n*y^2 = 1, for a positive non-square n.  Solutions can
         * be enormous: n = 61 gives x = 1766319049.
         */
        public static BigInteger[] pell(BigInteger n) {
            List<BigInteger> cf = sqrtContinuedFraction(n);
            int period = cf.size() - 1;
            // Convergent h/k at the end of the first period (or the second, if the period is odd).
            int terms = (period % 2 == 0) ? period : 2 * period;
            BigInteger h1 = O, h0 = cf.get(0);
            BigInteger k1 = Z, k0 = O;
            for (int i = 1; i <= terms - 1; i++) {
                BigInteger a = cf.get(1 + (i - 1) % period);
                BigInteger h = a.multiply(h0).add(h1);
                BigInteger k = a.multiply(k0).add(k1);
                h1 = h0;
                h0 = h;
                k1 = k0;
                k0 = k;
            }
            return new BigInteger[] {h0, k0};
        }

        // ── Bernoulli numbers, harmonic numbers, sums of powers ──────────────

        private static final int MAX_BERNOULLI = 20000;

        /**
         * The Bernoulli number B_n as an exact fraction, with the convention
         * B_1 = -1/2.  B_n is zero for odd n above 1.  Computed from the tangent
         * numbers with integer arithmetic only; n is limited to 20000.
         */
        public static Rational bernoulli(int n) {
            if (n < 0 || n > MAX_BERNOULLI) {
                throw new IllegalArgumentException("n must be from 0 to " + MAX_BERNOULLI);
            }
            if (n == 0) {
                return Rational.ONE;
            }
            if (n == 1) {
                return Rational.of(-1, 2);
            }
            if ((n & 1) == 1) {
                return Rational.ZERO;
            }
            int k = n / 2;
            BigInteger[] t = new BigInteger[k + 1];
            t[1] = O;
            for (int i = 2; i <= k; i++) {
                t[i] = t[i - 1].multiply(big(i - 1L));
            }
            for (int i = 2; i <= k; i++) {
                for (int j = i; j <= k; j++) {
                    t[j] = t[j - 1].multiply(big(j - i)).add(t[j].multiply(big(j - i + 2L)));
                }
            }
            BigInteger pow = O.shiftLeft(n);
            BigInteger num = t[k].multiply(big(n));
            BigInteger den = pow.multiply(pow.subtract(O));
            return Rational.of((k & 1) == 1 ? num : num.negate(), den);
        }

        /** The harmonic number H_n = 1 + 1/2 + ... + 1/n as an exact fraction (n &gt;= 0). */
        public static Rational harmonic(int n) {
            if (n < 0) {
                throw new IllegalArgumentException("n must be non-negative");
            }
            if (n == 0) {
                return Rational.ZERO;
            }
            BigInteger[] pq = harmonicRange(1, n + 1);
            return Rational.of(pq[0], pq[1]);
        }

        /** Sum of 1/i for lo &lt;= i &lt; hi, by binary splitting, as {numerator, denominator}. */
        private static BigInteger[] harmonicRange(int lo, int hi) {
            if (hi - lo == 1) {
                return new BigInteger[] {O, big(lo)};
            }
            int mid = (lo + hi) >>> 1;
            BigInteger[] a = harmonicRange(lo, mid);
            BigInteger[] b = harmonicRange(mid, hi);
            return new BigInteger[] {a[0].multiply(b[1]).add(b[0].multiply(a[1])), a[1].multiply(b[1])};
        }

        /**
         * The exact sum 1^k + 2^k + ... + n^k (Faulhaber's formula, using Bernoulli
         * numbers).  k and n are non-negative; k is limited to 20000.
         */
        public static BigInteger sumOfPowers(int k, BigInteger n) {
            if (k < 0 || n.signum() < 0) {
                throw new IllegalArgumentException("k and n must be non-negative");
            }
            if (k > MAX_BERNOULLI) {
                throw new IllegalArgumentException("k is too large");
            }
            // S = 1/(k+1) * sum_{j=0}^{k} C(k+1, j) * B_j(+) * n^(k+1-j), with B_1(+) = +1/2.
            // Work with a common denominator by scaling each Bernoulli number.
            BigInteger totalNum = Z;
            BigInteger totalDen = O;
            BigInteger binom = O;   // C(k+1, j)
            for (int j = 0; j <= k; j++) {
                if (j > 0) {
                    binom = binom.multiply(big(k + 2L - j)).divide(big(j));
                }
                Rational b = bernoulli(j);
                if (j == 1) {
                    b = Rational.of(1, 2);
                }
                if (b.num.signum() == 0) {
                    continue;
                }
                BigInteger termNum = binom.multiply(b.num).multiply(n.pow(k + 1 - j));
                BigInteger termDen = b.den;
                totalNum = totalNum.multiply(termDen).add(termNum.multiply(totalDen));
                totalDen = totalDen.multiply(termDen);
                BigInteger g = totalNum.gcd(totalDen);
                if (g.signum() != 0 && !g.equals(O)) {
                    totalNum = totalNum.divide(g);
                    totalDen = totalDen.divide(g);
                }
            }
            Rational result = Rational.of(totalNum, totalDen.multiply(big(k + 1L)));
            if (!result.den.equals(O)) {
                throw new IllegalStateException("Sum of powers was not an integer");
            }
            return result.num;
        }
    }

    // ── Finance ──────────────────────────────────────────────────────────────

    /**
     * Financial helpers built on exact fractions.
     *
     * <p>{@link #allocate} splits an amount of money in proportion to weights so
     * that the parts add back to exactly the original amount, with no cent lost
     * or invented.  For example 100.00 split three ways is 33.34, 33.33, 33.33.
     */
    public static final class Finance {
        private Finance() {
        }

        /** Where the units left over after rounding every share down are given. */
        public enum Remainder {
            /** One extra unit each to the shares with the largest fractional parts (ties: earlier share first). */
            LARGEST_REMAINDER,
            /** Extra units go to the first shares that have a non-zero weight, one each. */
            FIRST,
            /** Extra units go to the last shares that have a non-zero weight, one each. */
            LAST
        }

        private static final int MAX_PLACES = 1000;

        /**
         * Splits amount in proportion to weights, in whole units of 10^-places
         * (places = 2 gives cents), so that the shares sum to exactly amount.
         * The leftover units are given by {@link Remainder#LARGEST_REMAINDER}.
         *
         * @param amount  the amount to split; must be an exact multiple of 10^-places
         * @param places  decimal places of the smallest unit (2 for cents), 0 to 1000
         * @param weights non-negative weights (not all zero); a share with weight zero gets zero
         * @return the shares, in the order of the weights; a negative amount gives negative shares
         * @throws ArithmeticException if amount is not a multiple of the unit, a
         *         weight is negative, or the weights sum to zero
         */
        public static Rational[] allocate(Rational amount, int places, Rational... weights) {
            return allocate(amount, places, Remainder.LARGEST_REMAINDER, weights);
        }

        /** As {@link #allocate(Rational, int, Rational...)}, with a chosen rule for the leftover units. */
        public static Rational[] allocate(Rational amount, int places, Remainder rule, Rational... weights) {
            if (places < 0 || places > MAX_PLACES) {
                throw new IllegalArgumentException("places must be from 0 to " + MAX_PLACES);
            }
            Objects.requireNonNull(rule, "rule");
            if (weights.length == 0) {
                throw new ArithmeticException("At least one weight is required");
            }
            BigInteger unit = BigInteger.TEN.pow(places);
            Rational scaled = amount.multiply(Rational.of(unit));
            if (!scaled.isInteger()) {
                throw new ArithmeticException("Amount " + amount + " is not a multiple of one unit (10^-" + places + ")");
            }
            BigInteger total = scaled.num;
            boolean negative = total.signum() < 0;
            total = total.abs();

            // Weights as integers over a common denominator.
            BigInteger common = BigInteger.ONE;
            for (Rational w : weights) {
                if (w.signum() < 0) {
                    throw new ArithmeticException("Weights must not be negative");
                }
                common = NumberTheory.lcm(common, w.den);
            }
            BigInteger[] w = new BigInteger[weights.length];
            BigInteger weightSum = BigInteger.ZERO;
            for (int i = 0; i < w.length; i++) {
                w[i] = weights[i].num.multiply(common.divide(weights[i].den));
                weightSum = weightSum.add(w[i]);
            }
            if (weightSum.signum() == 0) {
                throw new ArithmeticException("Weights sum to zero");
            }

            BigInteger[] units = new BigInteger[w.length];
            BigInteger[] remainders = new BigInteger[w.length];
            BigInteger given = BigInteger.ZERO;
            for (int i = 0; i < w.length; i++) {
                BigInteger[] qr = total.multiply(w[i]).divideAndRemainder(weightSum);
                units[i] = qr[0];
                remainders[i] = qr[1];
                given = given.add(qr[0]);
            }
            int extra = total.subtract(given).intValueExact();   // fewer than w.length

            if (rule == Remainder.LARGEST_REMAINDER) {
                Integer[] order = new Integer[w.length];
                for (int i = 0; i < order.length; i++) {
                    order[i] = i;
                }
                java.util.Arrays.sort(order, (x, y) -> {
                    int c = remainders[y].compareTo(remainders[x]);
                    return c != 0 ? c : Integer.compare(x, y);
                });
                for (int k = 0; k < extra; k++) {
                    units[order[k]] = units[order[k]].add(BigInteger.ONE);
                }
            } else {
                int step = rule == Remainder.FIRST ? 1 : -1;
                int i = rule == Remainder.FIRST ? 0 : w.length - 1;
                while (extra > 0) {
                    if (w[i].signum() > 0) {
                        units[i] = units[i].add(BigInteger.ONE);
                        extra--;
                    }
                    i += step;
                }
            }

            Rational[] out = new Rational[w.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = Rational.of(negative ? units[i].negate() : units[i], unit);
            }
            return out;
        }

        /**
         * Splits amount into n equal shares to the nearest unit of 10^-places, the
         * first few shares being one unit larger when it does not divide evenly:
         * 100.00 split three ways is 33.34, 33.33, 33.33.
         */
        public static Rational[] allocateEvenly(Rational amount, int places, int n) {
            if (n < 1) {
                throw new IllegalArgumentException("n must be at least 1");
            }
            Rational[] weights = new Rational[n];
            java.util.Arrays.fill(weights, Rational.ONE);
            return allocate(amount, places, Remainder.LARGEST_REMAINDER, weights);
        }

        /** The exact sum of the values, for checking that an allocation adds back to the original. */
        public static Rational sum(Rational... values) {
            Rational total = Rational.ZERO;
            for (Rational v : values) {
                total = total.add(v);
            }
            return total;
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

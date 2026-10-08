import java.math.BigInteger;

/**
 * BigFixedArithmetic
 * 
 * Implements fixed-point arithmetic using BigInteger to prevent integer overflow.
 * All arithmetic operations (add, subtract, multiply, divide) are constructed
 * using only addition, subtraction, and bit shifting on BigInteger instances.
 */
public class BigFixedArithmetic {

    // Number of decimal places to preserve (can be set arbitrarily high)
    public static final int PRECISION = 18;

    private static final BigInteger SCALE;
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger TEN = BigInteger.valueOf(10);

    static {
        // Compute SCALE = 10^PRECISION using addition loops
        BigInteger s = ONE;
        for (int i = 0; i < PRECISION; i++) {
            BigInteger tenS = ZERO;
            for (int j = 0; j < 10; j++) {
                tenS = tenS.add(s);
            }
            s = tenS;
        }
        SCALE = s;
    }

    private final BigInteger regA; // Internal scaled value: realValue = regA / SCALE

    // ── Constructors & Factories ──────────────────────────────────────────────

    public BigFixedArithmetic(long integerValue) {
        this.regA = scaleUp(BigInteger.valueOf(integerValue));
    }

    private BigFixedArithmetic(BigInteger scaledValue, boolean alreadyScaled) {
        this.regA = scaledValue;
    }

    public static BigFixedArithmetic of(long value) {
        return new BigFixedArithmetic(value);
    }

    public static BigFixedArithmetic of(String decimal) {
        if (decimal == null || decimal.isEmpty()) {
            throw new IllegalArgumentException("Input string must not be empty");
        }

        int i = 0;
        BigInteger signum = ONE;
        if (decimal.charAt(0) == '-') {
            signum = ONE.negate();
            i++;
        } else if (decimal.charAt(0) == '+') {
            i++;
        }

        BigInteger intPart = ZERO;
        BigInteger fracPart = ZERO;
        long fracDigits = 0;
        boolean seenDot = false;

        while (i < decimal.length()) {
            char c = decimal.charAt(i);
            if (c == '.') {
                if (seenDot) throw new IllegalArgumentException("Multiple decimal points");
                seenDot = true;
            } else if (c >= '0' && c <= '9') {
                BigInteger digit = BigInteger.valueOf(c - '0');
                if (!seenDot) {
                    intPart = russianPeasant(intPart, TEN).add(digit);
                } else {
                    if (fracDigits < PRECISION) {
                        fracPart = russianPeasant(fracPart, TEN).add(digit);
                        fracDigits++;
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid character: " + c);
            }
            i++;
        }

        BigInteger scaled = russianPeasant(intPart, SCALE);

        if (fracDigits > 0) {
            BigInteger tenPow = ONE;
            for (long k = 0; k < fracDigits; k++) {
                tenPow = russianPeasant(tenPow, TEN);
            }
            BigInteger fracScaled = russianPeasant(fracPart, SCALE);
            BigInteger fracContribution = longDivide(fracScaled, tenPow);
            scaled = scaled.add(fracContribution);
        }

        return new BigFixedArithmetic(scaled.multiply(signum), true);
    }

    // ── Arithmetic Operations ─────────────────────────────────────────────────

    /** Addition: regA + other.regA */
    public BigFixedArithmetic add(BigFixedArithmetic other) {
        return new BigFixedArithmetic(this.regA.add(other.regA), true);
    }

    /** Subtraction: regA - other.regA */
    public BigFixedArithmetic subtract(BigFixedArithmetic other) {
        return new BigFixedArithmetic(this.regA.subtract(other.regA), true);
    }

    /**
     * Multiplication using Russian Peasant (binary shift-and-add) algorithm.
     */
    public BigFixedArithmetic multiply(BigFixedArithmetic other) {
        BigInteger sign = ONE;
        BigInteger regT = this.regA;
        if (regT.compareTo(ZERO) < 0) { regT = regT.negate(); sign = sign.negate(); }
        BigInteger regU = other.regA;
        if (regU.compareTo(ZERO) < 0) { regU = regU.negate(); sign = sign.negate(); }

        BigInteger T_int = longDivide(regT, SCALE);
        BigInteger T_frac = regT.subtract(russianPeasant(T_int, SCALE));

        BigInteger U_int = longDivide(regU, SCALE);
        BigInteger U_frac = regU.subtract(russianPeasant(U_int, SCALE));

        BigInteger term1 = russianPeasant(russianPeasant(T_int, U_int), SCALE);
        BigInteger term2 = russianPeasant(T_int, U_frac);
        BigInteger term3 = russianPeasant(T_frac, U_int);
        BigInteger term4 = longDivide(russianPeasant(T_frac, U_frac), SCALE);

        BigInteger result = term1.add(term2).add(term3).add(term4);
        if (sign.compareTo(ZERO) < 0) result = result.negate();

        return new BigFixedArithmetic(result, true);
    }

    /**
     * Division using long division via repeated subtraction and bit shifts.
     */
    public BigFixedArithmetic divide(BigFixedArithmetic other) {
        if (other.regA.equals(ZERO)) {
            throw new ArithmeticException("Division by zero");
        }

        BigInteger sign = ONE;
        BigInteger regT = this.regA;
        if (regT.compareTo(ZERO) < 0) { regT = regT.negate(); sign = sign.negate(); }
        BigInteger regU = other.regA;
        if (regU.compareTo(ZERO) < 0) { regU = regU.negate(); sign = sign.negate(); }

        BigInteger qInt = longDivide(regT, regU);
        BigInteger rem = regT.subtract(russianPeasant(qInt, regU));

        BigInteger remScaled = russianPeasant(rem, SCALE);
        BigInteger qFrac = longDivide(remScaled, regU);

        BigInteger result = russianPeasant(qInt, SCALE).add(qFrac);
        if (sign.compareTo(ZERO) < 0) result = result.negate();

        return new BigFixedArithmetic(result, true);
    }

    // ── Helper Algorithms ─────────────────────────────────────────────────────

    private static BigInteger scaleUp(BigInteger n) {
        return russianPeasant(n.abs(), SCALE).multiply(BigInteger.valueOf(n.signum()));
    }

    /** Russian-peasant (binary) multiplication using addition and bit shifts */
    private static BigInteger russianPeasant(BigInteger a, BigInteger b) {
        BigInteger result = ZERO;
        BigInteger ta = a;
        BigInteger tb = b;
        while (tb.compareTo(ZERO) > 0) {
            if (tb.testBit(0)) {
                result = result.add(ta);
            }
            ta = ta.add(ta);
            tb = tb.shiftRight(1);
        }
        return result;
    }

    /** Division via shift-and-subtract binary long division */
    private static BigInteger longDivide(BigInteger dividend, BigInteger divisor) {
        if (divisor.equals(ZERO)) throw new ArithmeticException("Division by zero");

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

    @Override
    public String toString() {
        BigInteger abs = regA.abs();
        BigInteger ip = longDivide(abs, SCALE);
        BigInteger frac = abs.subtract(russianPeasant(ip, SCALE));

        StringBuilder fracStr = new StringBuilder(frac.toString());
        while (fracStr.length() < PRECISION) {
            fracStr.insert(0, "0");
        }
        
        // Trim trailing zeros
        int end = fracStr.length();
        while (end > 0 && fracStr.charAt(end - 1) == '0') {
            end--;
        }
        String trimmedFrac = end == 0 ? "0" : fracStr.substring(0, end);

        String sign = regA.compareTo(ZERO) < 0 ? "-" : "";
        return sign + ip.toString() + "." + trimmedFrac;
    }

    // ── Demonstration ─────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=== BigInteger Fixed-Point Demo (PRECISION=" + PRECISION + ") ===\n");

        BigFixedArithmetic a = BigFixedArithmetic.of("123456789.987654321");
        BigFixedArithmetic b = BigFixedArithmetic.of("0.333333333333333333");

        System.out.println("a            = " + a);
        System.out.println("b            = " + b);
        System.out.println("a + b        = " + a.add(b));
        System.out.println("a - b        = " + a.subtract(b));
        System.out.println("a * b        = " + a.multiply(b));
        System.out.println("a / b        = " + a.divide(b));
        System.out.println("1 / 3        = " + BigFixedArithmetic.of(1).divide(BigFixedArithmetic.of(3)));
    }
}

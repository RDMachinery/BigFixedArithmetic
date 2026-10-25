import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * NumberTheoryDemo - a demonstration of BigFixedArithmetic.NumberTheory.
 *
 * Eight short scenes, each using the library the way a number-theory problem
 * naturally uses it:
 *
 *   1. Extended gcd and modular inverses
 *   2. A toy RSA key: primes, inverses, and what factoring does to it
 *   3. The Chinese remainder theorem (Sun Tzu's puzzle, and an impossible system)
 *   4. Factorising and the arithmetic functions (phi, tau, sigma, mu, lambda)
 *   5. Why 1/7 repeats every 6 digits: multiplicative order and primitive roots
 *   6. Farey sequences and the Stern-Brocot tree
 *   7. Pell's equation
 *   8. Bernoulli numbers, harmonic numbers and sums of powers, exactly
 *
 * Build and run (BigFixedArithmetic.java must be in the same folder):
 *
 *   javac BigFixedArithmetic.java NumberTheoryDemo.java
 *   java NumberTheoryDemo
 *
 * Copyright (c) 2026 Mario Gianota. Released under the MIT licence.
 */
public class NumberTheoryDemo {

    private static BigInteger b(long v) {
        return BigInteger.valueOf(v);
    }

    private static void heading(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            sb.append(' ');
        }
        return sb.append(s).toString();
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            throw new IllegalStateException("Check failed: " + what);
        }
    }

    // ── 1. extended gcd ──────────────────────────────────────────────────────

    private static void extendedGcdScene() {
        heading("1. Extended gcd and modular inverses");
        BigFixedArithmetic.NumberTheory.Bezout e = BigFixedArithmetic.NumberTheory.extendedGcd(b(240), b(46));
        System.out.println("extendedGcd(240, 46)  : " + e);
        System.out.println("check                 : 240 x " + e.x + " + 46 x " + e.y + " = " + b(240).multiply(e.x).add(b(46).multiply(e.y)));
        check("bezout", b(240).multiply(e.x).add(b(46).multiply(e.y)).equals(e.g));

        BigInteger inv = BigFixedArithmetic.NumberTheory.modInverse(b(17), b(3120));
        System.out.println("modInverse(17, 3120)  : " + inv + "   (17 x " + inv + " mod 3120 = " + b(17).multiply(inv).mod(b(3120)) + ")");
        try {
            BigFixedArithmetic.NumberTheory.modInverse(b(6), b(9));
        } catch (ArithmeticException ex) {
            System.out.println("modInverse(6, 9)      : " + ex.getMessage());
        }
        BigFixedArithmetic.Rational g = BigFixedArithmetic.NumberTheory.gcd(
                BigFixedArithmetic.Rational.of(3, 4), BigFixedArithmetic.Rational.of(9, 8), BigFixedArithmetic.Rational.of(15, 2));
        BigFixedArithmetic.Rational l = BigFixedArithmetic.NumberTheory.lcm(
                BigFixedArithmetic.Rational.of(3, 4), BigFixedArithmetic.Rational.of(9, 8));
        System.out.println("gcd(3/4, 9/8, 15/2)   : " + g + "      lcm(3/4, 9/8) : " + l
                + "   (two gears turning 3/4 and 9/8 times a minute line up every " + l + " minutes)");
    }

    // ── 2. toy RSA ───────────────────────────────────────────────────────────

    private static void rsaScene() {
        heading("2. A toy RSA key, and why factoring breaks it");
        BigInteger p = BigFixedArithmetic.NumberTheory.nextPrime(new BigInteger("1000000000000"));
        BigInteger q = BigFixedArithmetic.NumberTheory.nextPrime(new BigInteger("2000000000000"));
        BigInteger n = p.multiply(q);
        BigInteger phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
        BigInteger e = b(65537);
        BigInteger d = BigFixedArithmetic.NumberTheory.modInverse(e, phi);

        System.out.println("p = " + p + "  prime? " + BigFixedArithmetic.NumberTheory.isPrime(p));
        System.out.println("q = " + q + "  prime? " + BigFixedArithmetic.NumberTheory.isPrime(q));
        System.out.println("n = p x q = " + n);
        System.out.println("public exponent e = " + e + ", private exponent d = " + d);

        BigInteger message = new BigInteger("123456789012345678901");
        message = message.mod(n);
        BigInteger cipher = message.modPow(e, n);
        BigInteger plain = cipher.modPow(d, n);
        System.out.println("message    : " + message);
        System.out.println("encrypted  : " + cipher);
        System.out.println("decrypted  : " + plain);
        check("rsa round trip", plain.equals(message));

        // An attacker who can factor n recovers the private key.
        long start = System.nanoTime();
        Map<BigInteger, Integer> factors = BigFixedArithmetic.NumberTheory.factorize(n);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("factorize(n) found " + factors.keySet() + " in " + ms + " ms");
        BigInteger phiFromFactors = BigFixedArithmetic.NumberTheory.eulerPhi(n);
        BigInteger stolen = BigFixedArithmetic.NumberTheory.modInverse(e, phiFromFactors);
        System.out.println("private key recovered from the public key: " + stolen.equals(d));
        check("key recovery", stolen.equals(d) && phiFromFactors.equals(phi));
        System.out.println("(A teaching toy only: real keys use primes of hundreds of digits, whose products no known method can factor in practice.)");
    }

    // ── 3. CRT ───────────────────────────────────────────────────────────────

    private static void crtScene() {
        heading("3. The Chinese remainder theorem");
        BigFixedArithmetic.NumberTheory.Congruence c = BigFixedArithmetic.NumberTheory.crt(
                new BigInteger[] {b(2), b(3), b(2)}, new BigInteger[] {b(3), b(5), b(7)});
        System.out.println("Sun Tzu: a number leaves 2 mod 3, 3 mod 5, 2 mod 7  ->  " + c);
        check("sun tzu", c.residue.equals(b(23)) && c.modulus.equals(b(105)));

        BigFixedArithmetic.NumberTheory.Congruence shared = BigFixedArithmetic.NumberTheory.crt(
                new BigInteger[] {b(4), b(10)}, new BigInteger[] {b(6), b(15)});
        System.out.println("Moduli that share factors, x = 4 (mod 6), x = 10 (mod 15)  ->  " + shared);

        try {
            BigFixedArithmetic.NumberTheory.crt(new BigInteger[] {b(1), b(2)}, new BigInteger[] {b(4), b(6)});
        } catch (ArithmeticException ex) {
            System.out.println("x = 1 (mod 4) and x = 2 (mod 6)  ->  " + ex.getMessage());
        }
    }

    // ── 4. factorisation and arithmetic functions ────────────────────────────

    private static void functionsScene() {
        heading("4. Factorising and the arithmetic functions");
        BigInteger big = new BigInteger("1000000007").multiply(new BigInteger("998244353"));
        System.out.println("factorize(" + big + ") = " + BigFixedArithmetic.NumberTheory.factorizationString(big));
        System.out.println("factorize(600851475143) = " + BigFixedArithmetic.NumberTheory.factorizationString(new BigInteger("600851475143")));
        System.out.println();

        System.out.println(pad("n", 5) + pad("factors", 16) + pad("phi", 6) + pad("tau", 5) + pad("sigma", 7) + pad("mu", 4)
                + pad("lambda", 8) + pad("radical", 9) + "  kind");
        for (int n = 1; n <= 30; n++) {
            BigInteger bn = b(n);
            BigInteger sigma = BigFixedArithmetic.NumberTheory.divisorSum(bn, 1);
            String kind = sigma.equals(b(2L * n)) ? "perfect" : sigma.compareTo(b(2L * n)) > 0 ? "abundant" : "";
            System.out.println(pad(String.valueOf(n), 5)
                    + pad(BigFixedArithmetic.NumberTheory.factorizationString(bn), 16)
                    + pad(BigFixedArithmetic.NumberTheory.eulerPhi(bn).toString(), 6)
                    + pad(BigFixedArithmetic.NumberTheory.divisorCount(bn).toString(), 5)
                    + pad(sigma.toString(), 7)
                    + pad(String.valueOf(BigFixedArithmetic.NumberTheory.mobius(bn)), 4)
                    + pad(BigFixedArithmetic.NumberTheory.carmichaelLambda(bn).toString(), 8)
                    + pad(BigFixedArithmetic.NumberTheory.radical(bn).toString(), 9)
                    + "  " + kind);
        }
        System.out.println("divisors(360) = " + BigFixedArithmetic.NumberTheory.divisors(b(360)));
        BigInteger sumPhi = BigInteger.ZERO;
        for (BigInteger d : BigFixedArithmetic.NumberTheory.divisors(b(360))) {
            sumPhi = sumPhi.add(BigFixedArithmetic.NumberTheory.eulerPhi(d));
        }
        System.out.println("sum of phi(d) over the divisors of 360 = " + sumPhi + "  (Gauss: always equals 360)");
        check("gauss", sumPhi.equals(b(360)));
    }

    // ── 5. order and primitive roots ─────────────────────────────────────────

    private static void orderScene() {
        heading("5. Why 1/7 repeats every 6 digits");
        System.out.println(pad("p", 4) + pad("1/p", 28) + pad("period", 8) + pad("ord of 10", 11) + pad("prim. root", 11) + "  full reptend?");
        for (int p : new int[] {3, 7, 11, 13, 17, 19, 23, 29, 97}) {
            BigFixedArithmetic.Rational r = BigFixedArithmetic.Rational.of(1, p);
            long period = r.periodLength();
            BigInteger order = BigFixedArithmetic.NumberTheory.multiplicativeOrder(b(10), b(p));
            check("period equals order " + p, order.equals(b(period)));
            String shown = r.toRepeatingString();
            if (shown.length() > 26) {
                shown = shown.substring(0, 23) + "...";
            }
            System.out.println(pad(String.valueOf(p), 4) + pad(shown, 28) + pad(String.valueOf(period), 8)
                    + pad(order.toString(), 11) + pad(BigFixedArithmetic.NumberTheory.primitiveRoot(b(p)).toString(), 11)
                    + "  " + (period == p - 1 ? "yes" : "no"));
        }
        System.out.println("The length of the repeating block of 1/p is the multiplicative order of 10 mod p;");
        System.out.println("it is p - 1 exactly when 10 is a primitive root, which makes 1/p a cyclic number.");
        System.out.println("order of 2 mod 1000003 : " + BigFixedArithmetic.NumberTheory.multiplicativeOrder(b(2), b(1000003)));
    }

    // ── 6. Farey and Stern-Brocot ────────────────────────────────────────────

    private static void fareyScene() {
        heading("6. Farey sequences and the Stern-Brocot tree");
        List<BigFixedArithmetic.Rational> f5 = BigFixedArithmetic.NumberTheory.fareySequence(5);
        System.out.println("Farey sequence F5 : " + f5);
        for (int i = 0; i + 1 < f5.size(); i++) {
            check("neighbours", BigFixedArithmetic.NumberTheory.areFareyNeighbours(f5.get(i), f5.get(i + 1)));
        }
        System.out.println("Every adjacent pair satisfies |ad - bc| = 1, and the mediant of 1/3 and 2/5 is "
                + BigFixedArithmetic.NumberTheory.mediant(BigFixedArithmetic.Rational.of(1, 3), BigFixedArithmetic.Rational.of(2, 5)));
        System.out.println("F1000 has " + BigFixedArithmetic.NumberTheory.fareySequence(1000).size() + " terms");

        BigFixedArithmetic.Rational pi = BigFixedArithmetic.Rational.parse("3.14159265358979");
        BigFixedArithmetic.Rational[] nb = BigFixedArithmetic.NumberTheory.fareyNeighbours(pi, 100);
        System.out.println("Fractions closest to pi with denominator at most 100: below " + nb[0] + ", above " + nb[1]);

        for (String text : new String[] {"1", "3/2", "5/3", "355/113"}) {
            BigFixedArithmetic.Rational r = BigFixedArithmetic.Rational.parse(text);
            String path = BigFixedArithmetic.NumberTheory.sternBrocotPath(r);
            check("path round trip " + text, BigFixedArithmetic.NumberTheory.fromSternBrocotPath(path).equals(r));
            System.out.println("Stern-Brocot path to " + text + " : " + (path.isEmpty() ? "(root)" : path));
        }
    }

    // ── 7. Pell ──────────────────────────────────────────────────────────────

    private static void pellScene() {
        heading("7. Pell's equation x^2 - n y^2 = 1");
        System.out.println("continued fraction of sqrt(61) : " + BigFixedArithmetic.NumberTheory.sqrtContinuedFraction(b(61))
                + "   (first term, then the repeating period)");
        for (int n : new int[] {2, 13, 61, 109, 661, 991}) {
            BigInteger[] xy = BigFixedArithmetic.NumberTheory.pell(b(n));
            BigInteger lhs = xy[0].multiply(xy[0]).subtract(b(n).multiply(xy[1]).multiply(xy[1]));
            check("pell " + n, lhs.equals(BigInteger.ONE));
            System.out.println("n = " + pad(String.valueOf(n), 4) + "  x = " + xy[0] + ",  y = " + xy[1]);
        }
        System.out.println("Each solution was verified: x^2 - n y^2 = 1 exactly.");
        BigInteger[] xy = BigFixedArithmetic.NumberTheory.pell(b(2));
        System.out.println("x/y for n = 2 is " + BigFixedArithmetic.Rational.of(xy[0], xy[1]) + ", a convergent of sqrt(2) = "
                + BigFixedArithmetic.Rational.of(2).sqrt(BigFixedArithmetic.Rational.Context.significantDigits(10)).toScientificString(10, java.math.RoundingMode.HALF_UP));
    }

    // ── 8. exact series ──────────────────────────────────────────────────────

    private static void seriesScene() {
        heading("8. Bernoulli numbers, harmonic numbers and sums of powers");
        System.out.print("Bernoulli numbers : ");
        for (int n : new int[] {0, 1, 2, 4, 6, 8, 10, 12, 20}) {
            System.out.print("B" + n + " = " + BigFixedArithmetic.NumberTheory.bernoulli(n) + (n == 20 ? "" : ", "));
        }
        System.out.println();
        System.out.println("B50 = " + BigFixedArithmetic.NumberTheory.bernoulli(50));

        BigFixedArithmetic.Rational h20 = BigFixedArithmetic.NumberTheory.harmonic(20);
        System.out.println("H20 (exactly) = " + h20 + "  = about "
                + h20.toDecimalString(10, java.math.RoundingMode.HALF_UP));
        System.out.println("H100 numerator has " + BigFixedArithmetic.NumberTheory.harmonic(100).num.toString().length() + " digits");

        BigInteger n = b(1000);
        BigInteger formula = BigFixedArithmetic.NumberTheory.sumOfPowers(10, n);
        BigInteger direct = BigInteger.ZERO;
        for (int i = 1; i <= 1000; i++) {
            direct = direct.add(b(i).pow(10));
        }
        System.out.println("1^10 + 2^10 + ... + 1000^10 by Faulhaber's formula : " + formula);
        System.out.println("the same by adding them one at a time              : " + direct + "  (equal: " + formula.equals(direct) + ")");
        check("faulhaber", formula.equals(direct));
        System.out.println("Sum of the first 1,000,000 cubes : " + BigFixedArithmetic.NumberTheory.sumOfPowers(3, b(1000000))
                + "  (= (n(n+1)/2)^2)");
    }

    // ── main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("BigFixedArithmetic - number theory demo");
        extendedGcdScene();
        rsaScene();
        crtScene();
        functionsScene();
        orderScene();
        fareyScene();
        pellScene();
        seriesScene();
        System.out.println();
        System.out.println("Every result above was verified as it was produced; the demo stops with an error if any check fails.");
    }
}

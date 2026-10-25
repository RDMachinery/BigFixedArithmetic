import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/**
 * EngineeringDemo - a demonstration of BigFixedArithmetic.Engineering.
 *
 * Six scenes from electronics and signal work, each answering a question an
 * engineer really asks, with the error shown exactly (and in parts per million):
 *
 *   1. Which crystal gives the best UART baud rates?
 *   2. Making awkward clock frequencies from a 25 MHz reference with a PLL
 *   3. Sample-rate conversion factors
 *   4. Resistors: dividers, regulator feedback networks and E-series values
 *   5. Fixed-point (Q15) filter coefficients and why 0.1 is never exact in binary
 *   6. Choosing gear tooth counts
 *
 * Build and run (BigFixedArithmetic.java must be in the same folder):
 *
 *   javac BigFixedArithmetic.java EngineeringDemo.java
 *   java EngineeringDemo
 *
 * Copyright (c) 2026 Mario Gianota. Released under the MIT licence.
 */
public class EngineeringDemo {

    private static BigFixedArithmetic.Rational r(String text) {
        return BigFixedArithmetic.Rational.parse(text);
    }

    private static BigFixedArithmetic.Rational r(long n) {
        return BigFixedArithmetic.Rational.of(n);
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

    private static String left(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** A relative error shown in parts per million, e.g. "-35493.8". */
    private static String ppm(BigFixedArithmetic.Rational relativeError) {
        return relativeError.multiply(r(1_000_000)).toDecimalString(1, RoundingMode.HALF_UP);
    }

    /** A value shown to a number of decimal places (the library trims trailing zeros). */
    private static String dec(BigFixedArithmetic.Rational x, int places) {
        return x.toDecimalString(places, RoundingMode.HALF_UP);
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            throw new IllegalStateException("Check failed: " + what);
        }
    }

    // ── 1. UART baud rates ───────────────────────────────────────────────────

    private static void uartScene() {
        heading("1. Which crystal gives the best UART baud rates? (16x oversampling; divisor, then error in ppm)");
        String[] clockNames = {"8 MHz", "11.0592 MHz", "12 MHz", "16 MHz", "20 MHz"};
        BigFixedArithmetic.Rational[] clocks = {r(8_000_000), r(11_059_200), r(12_000_000), r(16_000_000), r(20_000_000)};
        long[] bauds = {9600, 19200, 38400, 115200};

        StringBuilder header = new StringBuilder(left("Clock", 13));
        for (long baud : bauds) {
            header.append(pad(baud + " baud", 22));
        }
        System.out.println(header + pad("worst (ppm)", 14));

        int best = -1;
        BigFixedArithmetic.Rational bestWorst = null;
        for (int c = 0; c < clocks.length; c++) {
            StringBuilder row = new StringBuilder(left(clockNames[c], 13));
            BigFixedArithmetic.Rational worst = BigFixedArithmetic.Rational.ZERO;
            for (long baud : bauds) {
                BigFixedArithmetic.Engineering.Divider d = BigFixedArithmetic.Engineering.uartDivisor(clocks[c], baud, 16, 65535);
                row.append(pad("/" + d.divisor + " (" + ppm(d.relativeError) + ")", 22));
                if (d.relativeError.abs().compareTo(worst) > 0) {
                    worst = d.relativeError.abs();
                }
            }
            System.out.println(row + pad(ppm(worst), 14));
            if (bestWorst == null || worst.compareTo(bestWorst) < 0) {
                bestWorst = worst;
                best = c;
            }
        }
        System.out.println("Best choice: " + clockNames[best] + ", worst error " + ppm(bestWorst) + " ppm.");
        System.out.println("A 16 MHz clock makes 115200 baud with a divisor of 9, which is "
                + ppm(BigFixedArithmetic.Engineering.uartDivisor(r(16_000_000), 115200, 16, 65535).relativeError)
                + " ppm out - too far for reliable serial links. 11.0592 MHz is an odd-looking crystal frequency"
                + " precisely because it divides evenly into the standard baud rates from 9600 up to 115200.");
        check("11.0592 MHz wins", best == 1);
        check("11.0592 MHz is exact at 115200",
                BigFixedArithmetic.Engineering.uartDivisor(r(11_059_200), 115200, 16, 65535).errorHz.signum() == 0);
    }

    // ── 2. PLL ───────────────────────────────────────────────────────────────

    private static void pllScene() {
        heading("2. Making clocks from a 25 MHz reference: output = reference x M / D");
        BigFixedArithmetic.Rational reference = r(25_000_000);
        String[] names = {"audio master 12.288 MHz (256 x 48 kHz)", "HDMI pixel clock 74.25 MHz", "USB 48 MHz", "CPU 133.333 MHz"};
        BigFixedArithmetic.Rational[] targets = {r(12_288_000), r(74_250_000), r(48_000_000), r(400_000_000).divide(r(3))};

        System.out.println(left("Target", 40) + pad("M/D (limit 1000)", 18) + pad("ppm", 8) + pad("M/D (limit 16)", 18) + pad("ppm", 10));
        for (int i = 0; i < targets.length; i++) {
            BigFixedArithmetic.Engineering.Synthesis wide = BigFixedArithmetic.Engineering.synthesize(reference, targets[i], 1000, 1000);
            BigFixedArithmetic.Engineering.Synthesis narrow = BigFixedArithmetic.Engineering.synthesize(reference, targets[i], 16, 16);
            System.out.println(left(names[i], 40)
                    + pad(wide.multiplier + "/" + wide.divider, 18) + pad(ppm(wide.relativeError), 8)
                    + pad(narrow.multiplier + "/" + narrow.divider, 18) + pad(ppm(narrow.relativeError), 10));
            check("wide limit is at least as good", wide.relativeError.abs().compareTo(narrow.relativeError.abs()) <= 0);
        }

        BigFixedArithmetic.Rational ratio = r(12_288_000).divide(reference);
        System.out.println();
        System.out.println("Ladder of ever-better ratios for 12.288/25 = " + ratio + " (continued-fraction convergents):");
        List<BigFixedArithmetic.Engineering.Ratio> ladder = BigFixedArithmetic.Engineering.convergentLadder(ratio, BigInteger.valueOf(10_000));
        for (BigFixedArithmetic.Engineering.Ratio step : ladder) {
            System.out.println("  " + pad(step.numerator + "/" + step.denominator, 12) + "  error " + pad(ppm(step.relativeError), 12) + " ppm");
        }
    }

    // ── 3. sample-rate conversion ────────────────────────────────────────────

    private static void sampleRateScene() {
        heading("3. Sample-rate conversion: upsample by L, filter, downsample by M");
        long[][] pairs = {{44100, 48000}, {48000, 44100}, {96000, 44100}, {22050, 48000}, {8000, 44100}, {44100, 88200}};
        System.out.println(pad("From", 8) + pad("To", 8) + pad("Up (L)", 8) + pad("Down (M)", 10) + pad("Intermediate rate", 20));
        for (long[] p : pairs) {
            BigInteger[] ud = BigFixedArithmetic.Engineering.sampleRateRatio(r(p[0]), r(p[1]));
            BigInteger intermediate = BigInteger.valueOf(p[0]).multiply(ud[0]);
            check("intermediate rate reaches both", intermediate.equals(BigInteger.valueOf(p[1]).multiply(ud[1])));
            System.out.println(pad(String.valueOf(p[0]), 8) + pad(String.valueOf(p[1]), 8) + pad(ud[0].toString(), 8)
                    + pad(ud[1].toString(), 10) + pad(intermediate + " Hz", 20));
        }
        System.out.println("The factors are exact and in lowest terms, so the filter runs at the lowest possible rate.");
    }

    // ── 4. resistors ─────────────────────────────────────────────────────────

    private static void resistorScene() {
        heading("4. Resistors");
        // (a) divider for 3.3 V from 5 V
        BigFixedArithmetic.Rational vin = r(5);
        BigFixedArithmetic.Rational vout = r("3.3");
        BigFixedArithmetic.Rational ratio = vout.divide(vin.subtract(vout));   // R2 / R1
        System.out.println("(a) A divider from 5 V to 3.3 V needs R2/R1 = " + ratio + " (R1 on top, R2 to ground).");
        System.out.println(pad("Series", 8) + pad("R1", 10) + pad("R2", 10) + pad("Vout", 12) + pad("error (ppm)", 14));
        for (int series : new int[] {12, 24, 96, 192}) {
            BigFixedArithmetic.Engineering.ResistorPair pair = BigFixedArithmetic.Engineering.bestStandardPair(ratio, series);
            BigFixedArithmetic.Rational r1 = pair.r1.multiply(r(1000));
            BigFixedArithmetic.Rational r2 = pair.r2.multiply(r(1000));
            BigFixedArithmetic.Rational actual = BigFixedArithmetic.Engineering.voltageDivider(vin, r1, r2);
            System.out.println(pad("E" + series, 8) + pad(dec(r1, 0) + "", 10) + pad(dec(r2, 0), 10)
                    + pad(dec(actual, 5) + " V", 12) + pad(ppm(actual.subtract(vout).divide(vout)), 14));
        }

        // (b) regulator feedback network
        BigFixedArithmetic.Rational vref = r("1.25");
        BigFixedArithmetic.Rational target = r("3.3");
        BigFixedArithmetic.Rational feedback = target.divide(vref).subtract(BigFixedArithmetic.Rational.ONE);   // Rtop / Rbot
        System.out.println();
        System.out.println("(b) An adjustable regulator with Vref = 1.25 V: Vout = Vref x (1 + Rtop/Rbot), so Rtop/Rbot = " + feedback + ".");
        for (int series : new int[] {12, 24, 96}) {
            BigFixedArithmetic.Engineering.ResistorPair pair = BigFixedArithmetic.Engineering.bestStandardPair(feedback, series);
            BigFixedArithmetic.Rational rbot = pair.r1.multiply(r(1000));
            BigFixedArithmetic.Rational rtop = pair.r2.multiply(r(1000));
            BigFixedArithmetic.Rational actual = vref.multiply(BigFixedArithmetic.Rational.ONE.add(rtop.divide(rbot)));
            System.out.println("    E" + left(String.valueOf(series), 4) + "Rbot " + pad(dec(rbot, 0), 7) + "  Rtop " + pad(dec(rtop, 0), 8)
                    + "  Vout = " + dec(actual, 5) + " V  (" + ppm(actual.subtract(target).divide(target)) + " ppm)");
        }

        // (c) combinations and nearest values
        BigFixedArithmetic.Rational a = r(4700);
        BigFixedArithmetic.Rational b = r(10000);
        BigFixedArithmetic.Rational parallel = BigFixedArithmetic.Engineering.parallelResistance(a, b);
        System.out.println();
        System.out.println("(c) 4.7k in series with 10k    : " + BigFixedArithmetic.Engineering.seriesResistance(a, b));
        System.out.println("    4.7k in parallel with 10k  : " + parallel + " = " + dec(parallel, 3) + " ohms, exactly");
        System.out.println("    nearest E12 to that        : " + BigFixedArithmetic.Engineering.nearestStandard(parallel, 12)
                + "    nearest E96: " + BigFixedArithmetic.Engineering.nearestStandard(parallel, 96));
        BigFixedArithmetic.Rational odd = r(47300);
        System.out.println("    nearest to 47,300 ohms     : E6 " + BigFixedArithmetic.Engineering.nearestStandard(odd, 6)
                + ", E24 " + BigFixedArithmetic.Engineering.nearestStandard(odd, 24)
                + ", E96 " + BigFixedArithmetic.Engineering.nearestStandard(odd, 96)
                + ", E192 " + BigFixedArithmetic.Engineering.nearestStandard(odd, 192));
        System.out.println("    the E12 decade: " + BigFixedArithmetic.Engineering.eSeries(12));
    }

    // ── 5. fixed point ───────────────────────────────────────────────────────

    private static void fixedPointScene() {
        heading("5. Fixed-point (Q15) filter coefficients");
        String[] names = {"0.1", "1/3", "sqrt(2)/2", "pi/4", "0.75", "-0.2"};
        BigFixedArithmetic.Rational[] values = {r("0.1"), BigFixedArithmetic.Rational.of(1, 3), r("0.70710678118654752"),
            r("0.78539816339744831"), r("0.75"), r("-0.2")};
        System.out.println(pad("Value", 11) + pad("Q15 integer", 13) + pad("fits int16", 12) + pad("stored value", 16) + pad("error", 12)
                + pad("bits for 1e-6", 15) + "  exact in binary?");
        for (int i = 0; i < values.length; i++) {
            BigFixedArithmetic.Engineering.Quantised q = BigFixedArithmetic.Engineering.quantise(values[i], 15, RoundingMode.HALF_EVEN);
            boolean fits = q.raw.compareTo(BigInteger.valueOf(-32768)) >= 0 && q.raw.compareTo(BigInteger.valueOf(32767)) <= 0;
            int bits = BigFixedArithmetic.Engineering.bitsNeeded(values[i], BigFixedArithmetic.Rational.of(1, 1_000_000));
            check("error is within half a step", q.error.abs().compareTo(BigFixedArithmetic.Rational.of(1, 65536)) <= 0);
            System.out.println(pad(names[i], 11) + pad(q.raw.toString(), 13) + pad(String.valueOf(fits), 12)
                    + pad(dec(q.represented, 10), 16) + pad(q.error.signum() == 0 ? "0" : q.error.toScientificString(2, RoundingMode.HALF_UP), 12)
                    + pad(String.valueOf(bits), 15) + "  " + BigFixedArithmetic.Engineering.isDyadic(values[i]));
        }
        System.out.println();
        System.out.println("0.1 in binary    : " + r("0.1").toRadixString(2, -1, RoundingMode.HALF_UP)
                + "   (the block repeats forever, so no finite word holds it)");
        System.out.println("0.75 in binary   : " + r("0.75").toRadixString(2, -1, RoundingMode.HALF_UP) + "   (exact)");
        System.out.println("Every Q15 error is within half a step (1/65536), checked exactly.");
    }

    // ── 6. gears ─────────────────────────────────────────────────────────────

    private static void gearScene() {
        heading("6. Gear tooth counts: the best ratio with at most N teeth on each wheel");
        BigFixedArithmetic.Rational target = r("3.14159265358979");
        System.out.println("Target ratio pi = 3.14159265358979");
        System.out.println(pad("Max teeth", 10) + pad("Gears", 12) + pad("Ratio", 14) + pad("error (ppm)", 14));
        for (long limit : new long[] {10, 30, 120, 500, 2000}) {
            BigFixedArithmetic.Engineering.Ratio best = BigFixedArithmetic.Engineering.bestRatio(target, limit, limit);
            System.out.println(pad(String.valueOf(limit), 10) + pad(best.numerator + ":" + best.denominator, 12)
                    + pad(dec(best.value, 8), 14) + pad(ppm(best.relativeError), 14));
        }
        System.out.println("355:113 needs a 355-tooth wheel, so with fewer teeth than that the best pair is 22:7.");
        BigFixedArithmetic.Engineering.Ratio hundred = BigFixedArithmetic.Engineering.bestRatio(target, 100, 100);
        check("both wheels within the limit", hundred.numerator.longValue() <= 100 && hundred.denominator.longValue() <= 100);
        BigFixedArithmetic.Engineering.Ratio lopsided = BigFixedArithmetic.Engineering.bestRatio(target, 400, 120);
        System.out.println("With at most 400 teeth on the large wheel and 120 on the small one: " + lopsided);
        check("limits are applied to each wheel separately", lopsided.numerator.longValue() <= 400 && lopsided.denominator.longValue() <= 120);
    }

    // ── main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("BigFixedArithmetic - engineering demo");
        uartScene();
        pllScene();
        sampleRateScene();
        resistorScene();
        fixedPointScene();
        gearScene();
        System.out.println();
        System.out.println("All errors above are exact fractions of the target, shown in parts per million.");
    }
}

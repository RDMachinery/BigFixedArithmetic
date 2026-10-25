import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * FinanceDemo - a demonstration of the financial methods in BigFixedArithmetic.
 *
 * It works through six everyday money problems where ordinary rounding loses or
 * invents a penny, and shows how exact fractions plus Finance.allocate keep every
 * total reconciled:
 *
 *   1. Splitting a restaurant bill with a service charge
 *   2. Dividing 100.00 evenly three ways
 *   3. Sharing a profit between partners (1/2, 1/3, 1/6) and the leftover-penny rules
 *   4. An invoice whose line-by-line VAT does not match the VAT on the total
 *   5. A loan repayment schedule that ends on a balance of exactly zero
 *   6. Currency conversion through an intermediate currency
 *
 * Build and run (BigFixedArithmetic.java must be in the same folder):
 *
 *   javac BigFixedArithmetic.java FinanceDemo.java
 *   java FinanceDemo
 *
 * Amounts are in a single unspecified currency with two decimal places.
 * Copyright (c) 2026 Mario Gianota. Released under the MIT licence.
 */
public class FinanceDemo {

    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    // ── formatting and small helpers ─────────────────────────────────────────

    private static BigFixedArithmetic.Rational r(String text) {
        return BigFixedArithmetic.Rational.parse(text);
    }

    /** Rounds to the nearest cent (half up), as an exact fraction. */
    private static BigFixedArithmetic.Rational cents(BigFixedArithmetic.Rational x) {
        return x.roundToDenominator(100, RoundingMode.HALF_UP);
    }

    /** Formats an amount to two decimal places with thousands separators, e.g. 1,234.50. */
    private static String money(BigFixedArithmetic.Rational x) {
        BigInteger c = x.multiply(BigFixedArithmetic.Rational.of(HUNDRED)).round(RoundingMode.HALF_UP);
        boolean negative = c.signum() < 0;
        BigInteger[] qr = c.abs().divideAndRemainder(HUNDRED);
        String whole = qr[0].toString();
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(whole.charAt(i));
        }
        String fraction = (qr[1].intValue() < 10 ? "0" : "") + qr[1];
        return (negative ? "-" : "") + grouped + "." + fraction;
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

    private static void heading(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    /** Stops the demo loudly if an allocation does not add back to exactly the original amount. */
    private static void requireSum(String what, BigFixedArithmetic.Rational[] parts, BigFixedArithmetic.Rational expected) {
        if (!BigFixedArithmetic.Finance.sum(parts).equals(expected)) {
            throw new IllegalStateException(what + " does not add back to " + expected);
        }
    }

    // ── 1. restaurant bill ───────────────────────────────────────────────────

    private static void restaurantBill() {
        heading("1. Splitting a restaurant bill with a 12.5% service charge");
        String[] names = {"Alice", "Ben", "Chloe", "Dev"};
        BigFixedArithmetic.Rational[] orders = {r("23.50"), r("41.20"), r("17.80"), r("12.00")};

        BigFixedArithmetic.Rational subtotal = BigFixedArithmetic.Finance.sum(orders);
        BigFixedArithmetic.Rational serviceExact = subtotal.multiply(r("0.125"));
        BigFixedArithmetic.Rational service = cents(serviceExact);

        // Naive approach: round each person's own 12.5% to the nearest cent.
        BigFixedArithmetic.Rational naiveTotal = BigFixedArithmetic.Rational.ZERO;
        for (BigFixedArithmetic.Rational o : orders) {
            naiveTotal = naiveTotal.add(cents(o.multiply(r("0.125"))));
        }

        // Exact approach: round the charge once, then share it in proportion to the orders.
        BigFixedArithmetic.Rational[] shares = BigFixedArithmetic.Finance.allocate(service, 2, orders);
        requireSum("Service charge shares", shares, service);

        System.out.println("Food subtotal           : " + money(subtotal));
        System.out.println("Service charge (exact)  : " + serviceExact.toRepeatingString() + "  -> charged as " + money(service));
        System.out.println("Each rounding their own : " + money(naiveTotal) + "  (off by " + money(naiveTotal.subtract(service)) + ")");
        System.out.println();
        System.out.println(left("Diner", 8) + pad("Food", 10) + pad("Service", 10) + pad("To pay", 10));
        BigFixedArithmetic.Rational grand = BigFixedArithmetic.Rational.ZERO;
        for (int i = 0; i < names.length; i++) {
            BigFixedArithmetic.Rational pay = orders[i].add(shares[i]);
            grand = grand.add(pay);
            System.out.println(left(names[i], 8) + pad(money(orders[i]), 10) + pad(money(shares[i]), 10) + pad(money(pay), 10));
        }
        System.out.println(left("Total", 8) + pad(money(subtotal), 10) + pad(money(service), 10) + pad(money(grand), 10));
        System.out.println("Table pays exactly the bill: " + grand.equals(subtotal.add(service)));
    }

    // ── 2. even split ────────────────────────────────────────────────────────

    private static void evenSplit() {
        heading("2. Dividing 100.00 three ways");
        BigFixedArithmetic.Rational amount = r("100.00");

        BigFixedArithmetic.Rational naiveShare = cents(amount.divide(BigFixedArithmetic.Rational.of(3)));
        BigFixedArithmetic.Rational naiveTotal = naiveShare.multiply(BigFixedArithmetic.Rational.of(3));
        System.out.println("Rounding each share : 3 x " + money(naiveShare) + " = " + money(naiveTotal)
                + "   (" + money(amount.subtract(naiveTotal)) + " has vanished)");

        BigFixedArithmetic.Rational[] parts = BigFixedArithmetic.Finance.allocateEvenly(amount, 2, 3);
        requireSum("Even split", parts, amount);
        StringBuilder sb = new StringBuilder();
        for (BigFixedArithmetic.Rational p : parts) {
            sb.append(sb.length() == 0 ? "" : " + ").append(money(p));
        }
        System.out.println("allocateEvenly      : " + sb + " = " + money(BigFixedArithmetic.Finance.sum(parts)));
    }

    // ── 3. partners and remainder rules ──────────────────────────────────────

    private static void partnerProfit() {
        heading("3. Sharing a profit of 10,000.01 between partners owning 1/2, 1/3 and 1/6");
        String[] names = {"Partner A (1/2)", "Partner B (1/3)", "Partner C (1/6)"};
        BigFixedArithmetic.Rational[] weights = {
            BigFixedArithmetic.Rational.of(1, 2), BigFixedArithmetic.Rational.of(1, 3), BigFixedArithmetic.Rational.of(1, 6)};
        BigFixedArithmetic.Rational profit = r("10000.01");

        BigFixedArithmetic.Finance.Remainder[] rules = BigFixedArithmetic.Finance.Remainder.values();
        BigFixedArithmetic.Rational[][] results = new BigFixedArithmetic.Rational[rules.length][];
        for (int k = 0; k < rules.length; k++) {
            results[k] = BigFixedArithmetic.Finance.allocate(profit, 2, rules[k], weights);
            requireSum("Profit share (" + rules[k] + ")", results[k], profit);
        }

        StringBuilder header = new StringBuilder(left("", 17));
        for (BigFixedArithmetic.Finance.Remainder rule : rules) {
            header.append(pad(rule.name(), 20));
        }
        System.out.println(header);
        for (int i = 0; i < names.length; i++) {
            StringBuilder row = new StringBuilder(left(names[i], 17));
            for (int k = 0; k < rules.length; k++) {
                row.append(pad(money(results[k][i]), 20));
            }
            System.out.println(row);
        }
        StringBuilder totals = new StringBuilder(left("Total", 17));
        for (int k = 0; k < rules.length; k++) {
            totals.append(pad(money(BigFixedArithmetic.Finance.sum(results[k])), 20));
        }
        System.out.println(totals);
        System.out.println("Every rule pays out exactly " + money(profit)
                + ". LARGEST_REMAINDER keeps each share within one penny of its exact value.");
    }

    // ── 4. invoice and VAT ───────────────────────────────────────────────────

    private static void invoiceVat() {
        heading("4. An invoice with 20% VAT");
        String[] items = {"Pen", "Ruler", "Paper clips", "Notebook"};
        BigFixedArithmetic.Rational[] net = {r("1.12"), r("1.12"), r("1.12"), r("2.34")};
        BigFixedArithmetic.Rational rate = r("0.20");

        BigFixedArithmetic.Rational netTotal = BigFixedArithmetic.Finance.sum(net);
        BigFixedArithmetic.Rational vatOnTotal = cents(netTotal.multiply(rate));

        BigFixedArithmetic.Rational vatLineSum = BigFixedArithmetic.Rational.ZERO;
        for (BigFixedArithmetic.Rational n : net) {
            vatLineSum = vatLineSum.add(cents(n.multiply(rate)));
        }
        System.out.println("VAT worked out line by line : " + money(vatLineSum));
        System.out.println("VAT on the invoice total    : " + money(vatOnTotal)
                + (vatLineSum.equals(vatOnTotal) ? "" : "   <- the two disagree by " + money(vatOnTotal.subtract(vatLineSum))));

        // The tax authority wants VAT on the total; share it back over the lines so the invoice adds up.
        BigFixedArithmetic.Rational[] vat = BigFixedArithmetic.Finance.allocate(vatOnTotal, 2, net);
        requireSum("VAT lines", vat, vatOnTotal);

        System.out.println();
        System.out.println(left("Item", 14) + pad("Net", 8) + pad("VAT", 8) + pad("Gross", 8));
        BigFixedArithmetic.Rational gross = BigFixedArithmetic.Rational.ZERO;
        for (int i = 0; i < items.length; i++) {
            BigFixedArithmetic.Rational g = net[i].add(vat[i]);
            gross = gross.add(g);
            System.out.println(left(items[i], 14) + pad(money(net[i]), 8) + pad(money(vat[i]), 8) + pad(money(g), 8));
        }
        System.out.println(left("Total", 14) + pad(money(netTotal), 8) + pad(money(vatOnTotal), 8) + pad(money(gross), 8));
        System.out.println("Lines add up to the invoice total: " + gross.equals(netTotal.add(vatOnTotal)));
    }

    // ── 5. loan schedule ─────────────────────────────────────────────────────

    private static void loanSchedule() {
        heading("5. A 12-month loan of 10,000.00 at 6.9% a year");
        BigFixedArithmetic.Rational principal = r("10000.00");
        BigFixedArithmetic.Rational monthlyRate = r("0.069").divide(BigFixedArithmetic.Rational.of(12));
        int months = 12;

        // Standard annuity payment, worked out exactly: P r (1+r)^n / ((1+r)^n - 1).
        BigFixedArithmetic.Rational growth = BigFixedArithmetic.Rational.ONE.add(monthlyRate).pow(months);
        BigFixedArithmetic.Rational paymentExact = principal.multiply(monthlyRate).multiply(growth)
                .divide(growth.subtract(BigFixedArithmetic.Rational.ONE));
        BigFixedArithmetic.Rational payment = cents(paymentExact);

        System.out.println("Monthly rate (exact)   : " + monthlyRate);
        System.out.println("Payment (exact)        : " + paymentExact.toDecimalString(6, RoundingMode.HALF_UP));
        System.out.println("Payment charged        : " + money(payment));
        System.out.println();
        System.out.println(pad("Month", 5) + pad("Payment", 12) + pad("Interest", 11) + pad("Principal", 12) + pad("Balance", 12));

        BigFixedArithmetic.Rational balance = principal;
        BigFixedArithmetic.Rational totalInterest = BigFixedArithmetic.Rational.ZERO;
        BigFixedArithmetic.Rational totalPaid = BigFixedArithmetic.Rational.ZERO;
        for (int m = 1; m <= months; m++) {
            BigFixedArithmetic.Rational interest = cents(balance.multiply(monthlyRate));
            BigFixedArithmetic.Rational pay = m == months ? balance.add(interest) : payment;   // last payment clears the balance
            BigFixedArithmetic.Rational toPrincipal = pay.subtract(interest);
            balance = balance.subtract(toPrincipal);
            totalInterest = totalInterest.add(interest);
            totalPaid = totalPaid.add(pay);
            System.out.println(pad(String.valueOf(m), 5) + pad(money(pay), 12) + pad(money(interest), 11)
                    + pad(money(toPrincipal), 12) + pad(money(balance), 12));
        }
        BigFixedArithmetic.Rational exactInterest = paymentExact.multiply(BigFixedArithmetic.Rational.of(months)).subtract(principal);
        System.out.println();
        System.out.println("Final balance is exactly zero : " + balance.equals(BigFixedArithmetic.Rational.ZERO));
        System.out.println("The final payment is adjusted by " + money(totalPaid.subtract(payment.multiply(BigFixedArithmetic.Rational.of(months))))
                + " so that the balance clears exactly");
        System.out.println("Interest charged " + money(totalInterest) + " against an exact " + money(exactInterest)
                + " (" + exactInterest.toDecimalString(6, RoundingMode.HALF_UP) + ")");
    }

    // ── 6. currency conversion ───────────────────────────────────────────────

    private static void currencyConversion() {
        heading("6. Converting 1,250.00 GBP to EUR through USD");
        BigFixedArithmetic.Rational amount = r("1250.00");
        BigFixedArithmetic.Rational gbpUsd = r("1.3127");
        BigFixedArithmetic.Rational usdEur = r("0.9065");

        BigFixedArithmetic.Rational usdRounded = cents(amount.multiply(gbpUsd));
        BigFixedArithmetic.Rational eurStepwise = cents(usdRounded.multiply(usdEur));
        BigFixedArithmetic.Rational eurExact = amount.multiply(gbpUsd).multiply(usdEur);
        BigFixedArithmetic.Rational eurOnce = cents(eurExact);

        System.out.println("GBP -> USD (rounded to cents)       : " + money(usdRounded));
        System.out.println("USD -> EUR from the rounded USD     : " + money(eurStepwise));
        System.out.println("GBP -> EUR, exact, rounded once     : " + money(eurOnce)
                + "   (exact value " + eurExact.toDecimalString(6, RoundingMode.HALF_UP) + ")");
        System.out.println("Cost of rounding in the middle      : " + money(eurStepwise.subtract(eurOnce)) + " (a penny that was never owed)");

        BigFixedArithmetic.Rational cross = gbpUsd.multiply(usdEur);
        System.out.println("Exact GBP/EUR cross rate            : " + cross.toRepeatingString());

        // Round trip at an exact rate and its exact reciprocal returns exactly the original amount.
        BigFixedArithmetic.Rational back = amount.multiply(gbpUsd).multiply(gbpUsd.reciprocal());
        System.out.println("GBP -> USD -> GBP at the exact rate : " + money(back) + " (identical: " + back.equals(amount) + ")");
    }

    // ── main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("BigFixedArithmetic - financial demo");
        System.out.println("Exact fractions; rounding to cents happens once, where a real ledger rounds.");
        restaurantBill();
        evenSplit();
        partnerProfit();
        invoiceVat();
        loanSchedule();
        currencyConversion();
        System.out.println();
        System.out.println("Every allocation above was checked to add back to exactly the original amount.");
    }
}

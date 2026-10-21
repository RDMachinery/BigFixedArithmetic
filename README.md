# BigFixedArithmetic

Exact arithmetic in a single Java file, built on `BigInteger`. It parses numbers from strings without losing a digit, divides exactly (repeating decimals are detected and shown as `0.(3)`), and includes a `Rational` fraction type with features `BigDecimal` doesn't have: repeating-decimal parsing, cycle analysis, expansion in any base, continued fractions, best approximations, and mixed-number and Egyptian-fraction output.

## Features

**Arithmetic and parsing**
- Exact addition, subtraction and multiplication (Russian Peasant) at any size, with no overflow.
- Decimal strings parsed exactly (`parseDecimal`, `parseInteger`): `"-12.375"` is held as `-12375` with 3 decimal places.
- Exact `Decimal` add, subtract and multiply.

**Division**
- **Exact mode** gives the complete result. A repeating expansion is written with its repeating block in parentheses: `1/3 = 0.(3)`, `1/6 = 0.1(6)`, `22/7 = 3.(142857)`. It always terminates, emits digits as they are computed, and uses constant memory.
- **Fixed-places mode** rounds to the places you ask for, using any `java.math.RoundingMode` (default `HALF_UP`).
- Both modes work in **any base from 2 to 36**: `1/3` is `0.1` in base 3 and `1/10` is `0.0(0011)` in base 2.

**`Rational`: an exact fraction type**
- Lowest-terms `BigInteger` fractions with `add`, `subtract`, `multiply`, `divide`, `pow`, `negate`, `reciprocal`.
- **Exact comparison** (`compareTo`, `equals`) that works even for values with no finite decimal form.
- **Floor, ceiling, truncate and rounding** (`round(mode)`), plus `roundToDenominator(n, mode)` to round to the nearest 1/n, for example the nearest sixteenth.
- **Parsing** of integers, decimals, repeating decimals (`0.(3)`, `0.1(6)`), fractions (`22/7`, `1.5/0.25`) and mixed numbers (`3 1/7`, `-3 1/7`), all exactly. `parse(text, base)` reads positional notation in any base.
- **Cycle analysis**: `preperiodLength()` and `periodLength()` (any base) report how many digits come before the repeating block and how long the block is, without generating digits.
- **Expansion in any base** with `toRadixString(base, places, mode)`, plus `toDecimalString` and `toRepeatingString`.
- **Continued fractions**: `continuedFraction()`, `convergents()`, `fromContinuedFraction(...)`.
- **Approximation**: `bestApproximation(maxDenominator)` finds the closest fraction with a limited denominator (pi to many digits with a limit of 1000 gives `355/113`). `approximate(x, tolerance)` and `fromDecimal(text)` recover the simplest fraction from a rounded value (`"0.142857"` gives `1/7`).
- **Formatting**: mixed numbers (`3 1/7`) and Egyptian fractions (`4/13 = 1/4 + 1/18 + 1/468`).

No dependencies beyond the JDK.

## Requirements

Java 11 or later (the single-file source launcher is used below).

## Build and run

```bash
javac BigFixedArithmetic.java
java BigFixedArithmetic
```

Or, without a separate compile step:

```bash
java BigFixedArithmetic.java
```

Example session (exact mode):

```
=== BigFixedArithmetic Demo ===
Numbers may be integers or decimals, e.g. 42, -0.5, 3.14159
Enter Dividend / First Operand: 355
Enter Divisor / Second Operand: 113

--- Basic Operations ---
Addition (a + b)       : 468
Subtraction (a - b)    : 242
Multiplication (a * b) : 40115

--- Division ---
Enter required decimal places (-1 for exact, repeating blocks shown like 0.(3)): -1
Division Result (a / b): 3.(1415929203539823008849557522123893805309734513274336283185840707964601769911504424778761061946902654867256637168)

--- Exact Fraction Forms of a / b ---
Fraction           : 355/113
Mixed number       : 3 16/113
Continued fraction : [3; 7, 16]
```

Choosing a number of places instead rounds the result: `2 / 3` to 5 places with `HALF_UP` prints `0.66667`.

## Using it as a library

Copy `BigFixedArithmetic.java` into your project.

### Division

```java
import java.math.BigInteger;
import java.math.RoundingMode;

BigInteger one = BigInteger.ONE, three = BigInteger.valueOf(3), six = BigInteger.valueOf(6);

BigFixedArithmetic.divideToString(one, three, -1, RoundingMode.HALF_UP);   // "0.(3)"
BigFixedArithmetic.divideToString(one, six,   -1, RoundingMode.HALF_UP);   // "0.1(6)"
BigFixedArithmetic.divideToString(BigInteger.TWO, three, 5, RoundingMode.HALF_UP);  // "0.66667"
BigFixedArithmetic.divideToString(BigInteger.TWO, three, 5, RoundingMode.DOWN);     // "0.66666"

// Other bases
BigFixedArithmetic.divideToString(one, three, -1, RoundingMode.HALF_UP, 3);          // "0.1"
BigFixedArithmetic.divideToString(one, BigInteger.TEN, -1, RoundingMode.HALF_UP, 2); // "0.0(0011)"
```

### Rational

```java
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

BigFixedArithmetic.Rational third = BigFixedArithmetic.Rational.parse("1/3");

// Exact arithmetic: no rounding anywhere
third.multiply(BigFixedArithmetic.Rational.of(3));        // 1
BigFixedArithmetic.Rational.parse("0.1").add(BigFixedArithmetic.Rational.parse("0.2"));  // 3/10

// Parsing every form
BigFixedArithmetic.Rational.parse("0.(3)");          // 1/3
BigFixedArithmetic.Rational.parse("0.1(6)");         // 1/6
BigFixedArithmetic.Rational.parse("3.(142857)");     // 22/7
BigFixedArithmetic.Rational.parse("-3 1/7");         // -22/7
BigFixedArithmetic.Rational.parse("1.5/0.25");       // 6
BigFixedArithmetic.Rational.parse("0.(0011)", 2);    // 1/5, read in base 2

// Exact comparison, floor, ceiling and rounding
BigFixedArithmetic.Rational x = BigFixedArithmetic.Rational.of(-22, 7);
x.floor();                                           // -4
x.ceil();                                            // -3
x.round(RoundingMode.HALF_UP);                       // -3
x.roundToDenominator(16, RoundingMode.HALF_UP);      // -25/8 (nearest sixteenth)

// Cycle information
BigFixedArithmetic.Rational seventh = BigFixedArithmetic.Rational.of(1, 7);
seventh.periodLength();                              // 6
BigFixedArithmetic.Rational.of(1, 12).preperiodLength();   // 2  (0.08(3))

// Any base
BigFixedArithmetic.Rational.of(1, 10).toRadixString(2, -1, RoundingMode.HALF_UP);   // "0.0(0011)"

// Continued fractions and approximation
BigFixedArithmetic.Rational r = BigFixedArithmetic.Rational.of(355, 113);
r.toContinuedFractionString();                       // "[3; 7, 16]"
r.convergents();                                     // [3, 22/7, 355/113]
BigFixedArithmetic.Rational pi = BigFixedArithmetic.Rational.parse("3.14159265358979323846");
pi.bestApproximation(1000);                          // 355/113
BigFixedArithmetic.Rational.fromDecimal("0.142857"); // 1/7

// Formatting
BigFixedArithmetic.Rational.of(22, 7).toMixedString();     // "3 1/7"
BigFixedArithmetic.Rational.of(4, 13).toEgyptianString();  // "1/4 + 1/18 + 1/468"
```

### API summary

**Static arithmetic**

| Method | Description |
|---|---|
| `add`, `subtract`, `multiply` on `BigInteger` or `Decimal` | Exact results |
| `longDivide(BigInteger, BigInteger)` | Integer quotient of two non-negative values |
| `divideToString(dividend, divisor, places, mode[, base])` | Quotient as text |
| `streamDivide(dividend, divisor, places[, mode])` | Prints the quotient and a newline |
| `parseDecimal(String)` / `parseInteger(String)` | Exact string parsing |

**`Rational`**

| Group | Methods |
|---|---|
| Create | `of(n, d)`, `of(n)`, `of(Decimal)`, `parse(text)`, `parse(text, base)`, `fromDecimal(text[, tolerance])`, `fromContinuedFraction(...)` |
| Arithmetic | `add`, `subtract`, `multiply`, `divide`, `pow`, `negate`, `abs`, `reciprocal`, `signum` |
| Compare | `compareTo`, `equals`, `isInteger` |
| Integer part | `floor`, `ceil`, `truncate`, `fractionalPart`, `round(mode)`, `roundToDenominator(n, mode)` |
| Cycles | `preperiodLength([base])`, `periodLength([base])` |
| Expansion | `toRadixString(base, places, mode)`, `toDecimalString(places, mode)`, `toRepeatingString()` |
| Continued fractions | `continuedFraction()`, `toContinuedFractionString()`, `convergents()` |
| Approximation | `bestApproximation(maxDenominator)`, `approximate(x, tolerance)` |
| Formatting | `toString()`, `toMixedString()`, `egyptianFractions()`, `toEgyptianString()` |

### Division modes

| `places` | Behaviour |
|---|---|
| `-1` | Exact result. A repeating block is shown in parentheses, for example `0.1(6)`. The rounding mode is ignored. |
| `0` or more | Rounded to that many places with the given `RoundingMode` (default `HALF_UP`). A quotient that divides out exactly in fewer places is printed without padding zeros (`24.75`, not `24.750000`). |

Supported rounding modes are `UP`, `DOWN`, `CEILING`, `FLOOR`, `HALF_UP`, `HALF_DOWN`, `HALF_EVEN` and `UNNECESSARY` (which throws `ArithmeticException` if rounding would be needed). A result that rounds to zero is printed as `0`, never `-0`. In bases other than 10, `HALF_EVEN` looks at whether the last digit, in that base, is odd.

### Accepted input formats

`parseDecimal` and `parseInteger` accept an optional `+` or `-`, digits, and at most one decimal point, with at least one digit overall: `42`, `-0.5`, `+3.14`, `.25`, `7.`. Surrounding whitespace is ignored.

`Rational.parse` also accepts a repeating block in parentheses after the decimal point (`0.(3)`, `12.34(56)`), fractions (`22/7`, `1.5/0.25`, with a repeating decimal allowed on either side), and mixed numbers (`3 1/7`, where the sign applies to the whole number). Fractions with a zero denominator throw `ArithmeticException`; anything else malformed throws `IllegalArgumentException`. Scientific notation (`1e5`) is not supported.

## How repeating decimals are detected

The remainders in long division must eventually repeat. For a reduced fraction, the number of digits before the repeating block depends only on the factors the denominator shares with the base. In base 10 it is the larger of the powers of 2 and 5, and a repeating block exists only if other prime factors remain. The code works that length out first, prints `(` at the right digit, and prints `)` the first time the remainder returns to the value it had at the `(`. No digits are buffered and only one remainder is stored. `periodLength()` is the multiplicative order of the base modulo the leftover factor, found by stepping through the remainders.

## Limitations

- `longDivide` expects non-negative arguments. The division methods and `Rational` handle signs for you.
- **Long repeating blocks.** Exact mode and `periodLength()` take as many steps as the period is long, and the period can be nearly as long as the divisor (for example 1 / 99999989). Both use constant memory, and exact mode streams digits the whole time.
- **Egyptian fractions** use the greedy method. Denominators can grow extremely fast, so results for some fractions are enormous.
- **Speed.** Multiplication and division are written out the long way (Russian Peasant and shift-and-subtract), so for very large numbers this is much slower than using `BigInteger` or `BigDecimal` directly. `Rational` uses these routines.
- Exactness applies to rational arithmetic. Irrational values (square roots, trigonometric functions, pi) cannot be represented exactly in any finite number of digits.

## Testing

The code was checked against independent references: `java.math.BigDecimal` for rounding, floor, ceiling and all rounding modes, and separate brute-force or map-based implementations for repeating expansions, cycle lengths, simplest-fraction and best-approximation searches (which verify the smallest denominator and the closest distance). Parsing and printing were round-tripped in every base from 2 to 36. In total about 260,000 checks ran with no differences.

## License

This software is released under the terms of the MIT license. See the [LICENSE](LICENSE) file for the full text.

Copyright (c) 2026 Mario Gianota

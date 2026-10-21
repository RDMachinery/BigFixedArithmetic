# BigFixedArithmetic

Exact arbitrary-precision arithmetic in a single Java file, built on `BigInteger`. It adds, subtracts, multiplies and divides integers and decimal numbers with no overflow. Division is exact: repeating decimals are detected and shown with their repeating block (`1/3 = 0.(3)`), or the result can be rounded to a fixed number of places with the rounding mode of your choice.

## Features

- **Exact addition and subtraction** at any size.
- **Multiplication** using the Russian Peasant (binary shift-and-add) algorithm.
- **Division** using shift-and-subtract binary long division, in two modes:
  - **Exact mode** produces the complete result. A repeating decimal is written with its repeating block in parentheses, for example `1/3 = 0.(3)`, `1/6 = 0.1(6)`, `22/7 = 3.(142857)`. It always terminates, emits digits as they are computed, and uses constant memory.
  - **Fixed-places mode** rounds to the number of decimal places you ask for, using any `java.math.RoundingMode` (default `HALF_UP`).
- **String parsing** (`parseDecimal`, `parseInteger`) that keeps every digit. `"-12.375"` is held exactly as `-12375` with 3 decimal places.
- **Exact decimal operations** (`add`, `subtract`, `multiply`, division) on the `Decimal` type returned by the parser.
- No dependencies beyond the JDK.

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

Example session (rounded to 6 places):

```
=== BigFixedArithmetic Demo ===
Numbers may be integers or decimals, e.g. 42, -0.5, 3.14159
Enter Dividend / First Operand: 12.375
Enter Divisor / Second Operand: 0.5

--- Basic Operations ---
Addition (a + b)       : 12.875
Subtraction (a - b)    : 11.875
Multiplication (a * b) : 6.1875

--- Division ---
Enter required decimal places (-1 for exact, repeating blocks shown like 0.(3)): 6
Rounding mode (HALF_UP, HALF_EVEN, HALF_DOWN, UP, DOWN, CEILING, FLOOR) [HALF_UP]:
Division Result (a / b): 24.75
```

Entering `-1` for the places gives the exact result. For `1 / 3` the program prints `0.(3)`, and for `2 / 3` with 5 places and `HALF_UP` it prints `0.66667`.

## Using it as a library

Copy `BigFixedArithmetic.java` into your project.

```java
import java.math.BigInteger;
import java.math.RoundingMode;

// Integers
BigInteger sum  = BigFixedArithmetic.add(new BigInteger("123"), new BigInteger("456"));
BigInteger prod = BigFixedArithmetic.multiply(new BigInteger("99999999999"), new BigInteger("99999999999"));

// Decimals from strings (exact, nothing is rounded)
BigFixedArithmetic.Decimal a = BigFixedArithmetic.parseDecimal("1.5");
BigFixedArithmetic.Decimal b = BigFixedArithmetic.parseDecimal("0.25");

System.out.println(BigFixedArithmetic.add(a, b));       // 1.75
System.out.println(BigFixedArithmetic.multiply(a, b));  // 0.375

// Division as a string
BigInteger one = BigInteger.ONE, three = BigInteger.valueOf(3), six = BigInteger.valueOf(6);

BigFixedArithmetic.divideToString(one, three, -1, RoundingMode.HALF_UP);  // "0.(3)"
BigFixedArithmetic.divideToString(one, six, -1, RoundingMode.HALF_UP);    // "0.1(6)"
BigFixedArithmetic.divideToString(BigInteger.TWO, three, 5, RoundingMode.HALF_UP);  // "0.66667"
BigFixedArithmetic.divideToString(BigInteger.TWO, three, 5, RoundingMode.DOWN);     // "0.66666"

// Or print straight to standard output
BigFixedArithmetic.streamDivide(a, b, -1);   // 6
```

### API summary

| Method | Description |
|---|---|
| `add(BigInteger, BigInteger)` | Exact sum |
| `subtract(BigInteger, BigInteger)` | Exact difference |
| `multiply(BigInteger, BigInteger)` | Exact product (Russian Peasant) |
| `longDivide(BigInteger, BigInteger)` | Integer quotient of two non-negative values |
| `divideToString(BigInteger, BigInteger, long places, RoundingMode)` | Quotient as text |
| `streamDivide(BigInteger, BigInteger, long places[, RoundingMode])` | Prints the quotient and a newline |
| `parseDecimal(String)` | Parses a decimal string into an exact `Decimal` |
| `parseInteger(String)` | Parses a whole-number string into a `BigInteger` |
| `add`, `subtract`, `multiply`, `divideToString`, `streamDivide` on `Decimal` | Exact decimal arithmetic |

### Division modes

| `places` | Behaviour |
|---|---|
| `-1` | Exact result. A repeating block is shown in parentheses, for example `0.1(6)`. The rounding mode is ignored. |
| `0` or more | Rounded to that many places with the given `RoundingMode` (default `HALF_UP`). A quotient that divides out exactly in fewer places is printed without padding zeros (`24.75`, not `24.750000`). |

Supported rounding modes are `UP`, `DOWN`, `CEILING`, `FLOOR`, `HALF_UP`, `HALF_DOWN`, `HALF_EVEN` and `UNNECESSARY` (which throws `ArithmeticException` if rounding would be needed). A result that rounds to zero is printed as `0`, never `-0`.

### Accepted input format

An optional `+` or `-`, digits, and at most one decimal point, with at least one digit overall. Examples: `42`, `-0.5`, `+3.14`, `.25`, `7.`. Surrounding whitespace is ignored. Scientific notation (`1e5`) and other characters are rejected with an `IllegalArgumentException`.

## How repeating decimals are detected

The remainders in long division must eventually repeat. For the reduced fraction, the number of digits before the repeating block is the larger of the powers of 2 and 5 in the reduced denominator, and a block exists only if other prime factors remain. The code works that length out first, prints `(` at the right digit, and prints `)` the first time the remainder returns to the value it had at the `(`. No digits are buffered and only one remainder is stored.

## Limitations

- `longDivide` expects non-negative arguments. The division methods handle signs for you.
- Exact mode terminates for every input, but if the repeating block is extremely long (the period can be nearly as long as the divisor, for example 1 / 99999989), it takes correspondingly long to finish. Digits stream out the whole time.
- Exactness applies to rational arithmetic. Irrational values (square roots, trigonometric functions, π) cannot be represented exactly in any finite number of digits.

## Testing

The division code was checked against `java.math.BigDecimal` (all rounding modes, random operands, carries such as `9.9996` to 3 places) and against an independent map-based cycle detector for exact mode, over more than 100,000 cases with no differences.

## License

This software is released under the terms of the MIT license. See the [LICENSE](LICENSE) file for the full text.

Copyright (c) 2026 Mario Gianota

# BigFixedArithmetic

Exact arbitrary-precision arithmetic in a single Java file, built on `BigInteger`. It adds, subtracts, multiplies and divides integers and decimal numbers with no overflow and no rounding, and its division engine streams decimal digits one at a time for as long as you ask.

## Features

- **Exact addition and subtraction** at any size.
- **Multiplication** using the Russian Peasant (binary shift-and-add) algorithm.
- **Division** using shift-and-subtract binary long division. `streamDivide` prints the integer part and then fractional digits one by one, each computed from the true remainder, so there is no accumulated rounding error.
- **String parsing** (`parseDecimal`, `parseInteger`) that keeps every digit. `"-12.375"` is held exactly as `-12375` with 3 decimal places.
- **Exact decimal operations** (`add`, `subtract`, `multiply`, `streamDivide`) on the `Decimal` type returned by the parser.
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

Example session:

```
=== BigFixedArithmetic Demo ===
Numbers may be integers or decimals, e.g. 42, -0.5, 3.14159
Enter Dividend / First Operand: 12.375
Enter Divisor / Second Operand: 0.5

--- Basic Operations ---
Addition (a + b)       : 12.875
Subtraction (a - b)    : 11.875
Multiplication (a * b) : 6.1875

--- Streaming Division ---
Enter required decimal places (-1 for unlimited streaming): 6
Division Result (a / b): 24.75
```

## Using it as a library

Copy `BigFixedArithmetic.java` into your project.

```java
import java.math.BigInteger;

// Integers
BigInteger sum  = BigFixedArithmetic.add(new BigInteger("123"), new BigInteger("456"));
BigInteger prod = BigFixedArithmetic.multiply(new BigInteger("99999999999"), new BigInteger("99999999999"));

// Decimals from strings (exact, nothing is rounded)
BigFixedArithmetic.Decimal a = BigFixedArithmetic.parseDecimal("1.5");
BigFixedArithmetic.Decimal b = BigFixedArithmetic.parseDecimal("0.25");

System.out.println(BigFixedArithmetic.add(a, b));       // 1.75
System.out.println(BigFixedArithmetic.multiply(a, b));  // 0.375

// Division: print up to 20 decimal places (use -1 for unlimited)
BigFixedArithmetic.streamDivide(a, b, 20);              // 6
BigFixedArithmetic.streamDivide(BigInteger.ONE, BigInteger.valueOf(3), 20);  // 0.33333333333333333333
```

### API summary

| Method | Description |
|---|---|
| `add(BigInteger, BigInteger)` | Exact sum |
| `subtract(BigInteger, BigInteger)` | Exact difference |
| `multiply(BigInteger, BigInteger)` | Exact product (Russian Peasant) |
| `longDivide(BigInteger, BigInteger)` | Integer quotient of two non-negative values |
| `streamDivide(BigInteger, BigInteger, long)` | Prints a quotient digit by digit; `-1` means unlimited |
| `parseDecimal(String)` | Parses a decimal string into an exact `Decimal` |
| `parseInteger(String)` | Parses a whole-number string into a `BigInteger` |
| `add`, `subtract`, `multiply`, `streamDivide` on `Decimal` | Exact decimal arithmetic |

### Accepted input format

An optional `+` or `-`, digits, and at most one decimal point, with at least one digit overall. Examples: `42`, `-0.5`, `+3.14`, `.25`, `7.`. Surrounding whitespace is ignored. Scientific notation (`1e5`) and other characters are rejected with an `IllegalArgumentException`.

## Limitations

- **Division truncates.** A capped run such as 2/3 to 5 places prints `0.66666`, not `0.66667`.
- **Unlimited mode (`-1`) does not stop for repeating decimals.** For results like 1/3 the digits never end, because the remainder cycles forever.
- `longDivide` expects non-negative arguments. `streamDivide` handles signs for you.
- Exactness applies to rational arithmetic. Irrational values (square roots, trigonometric functions, π) cannot be represented exactly in any finite number of digits.

## License

This software is released under the terms of the MIT license.

Copyright (c) 2026 Mario Gianota

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

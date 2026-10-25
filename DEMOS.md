# BigFixedArithmetic demos

Three small console programs that show the library at work on real problems. Each one prints its results as it goes and stops with an error if any of its own checks fail, so a clean run means every figure below was verified exactly.

For the library itself, see the main [README](README.md).

## Running a demo

Put `BigFixedArithmetic.java` and the demo in the same folder, then compile both and run:

```bash
javac BigFixedArithmetic.java FinanceDemo.java
java FinanceDemo
```

Replace `FinanceDemo` with `NumberTheoryDemo` or `EngineeringDemo` for the others. Java 11 or later is needed. No other libraries are used.

| Demo | File | Shows |
|---|---|---|
| [Finance](#finance-demo) | `FinanceDemo.java` | Splitting money so nothing is lost, and reconciling totals |
| [Number theory](#number-theory-demo) | `NumberTheoryDemo.java` | Primes, factoring, congruences, repeating decimals, Pell equations |
| [Engineering](#engineering-demo) | `EngineeringDemo.java` | Clock and baud-rate ratios, resistors, sample rates, fixed point |

## Finance demo

**The idea:** ordinary rounding loses or invents pennies. The demo keeps every amount as an exact fraction, rounds to cents once, and uses `Finance.allocate` so that shares always add back to the original amount.

Six scenes:

1. **Restaurant bill.** A 12.5% service charge is rounded once and shared in proportion to what each diner ordered. If everyone rounds their own share, the total is a penny too high.
2. **Even split.** 100.00 three ways. Rounding each share gives 99.99; `allocateEvenly` gives 33.34 + 33.33 + 33.33.
3. **Partner profits.** 10,000.01 split between partners owning 1/2, 1/3 and 1/6, under the three leftover-penny rules.
4. **Invoice VAT.** VAT worked out line by line disagrees with VAT on the total, so the total is shared back over the lines.
5. **Loan schedule.** A 12-month loan with an exact payment formula, rounded each month, with the last payment adjusted so the balance ends at exactly zero.
6. **Currency conversion.** Converting through an intermediate currency. Rounding in the middle costs a penny that was never owed.

```
Dividing 100.00 three ways
Rounding each share : 3 x 33.33 = 99.99   (0.01 has vanished)
allocateEvenly      : 33.34 + 33.33 + 33.33 = 100.00
```

## Number theory demo

**The idea:** a tour of `NumberTheory`, using each tool the way a number-theory problem naturally calls for it.

Eight scenes:

1. **Extended gcd and inverses.** Bézout coefficients, modular inverses for any modulus, and gcd and lcm of fractions.
2. **Toy RSA.** Two 13-digit primes, encryption, decryption, and then `factorize` recovering the private key from the public one. A teaching toy only.
3. **Chinese remainder theorem.** Sun Tzu's puzzle, moduli that share factors, and an impossible system.
4. **Arithmetic functions.** A table for 1 to 30 of φ, τ, σ, μ, Carmichael's λ and the radical, with perfect and abundant numbers marked.
5. **Repeating decimals.** The length of the repeating block of 1/p equals the multiplicative order of 10 mod p, and full-reptend primes are those where 10 is a primitive root.
6. **Farey and Stern-Brocot.** Farey sequences, mediants, neighbours of π, and tree paths.
7. **Pell's equation.** Fundamental solutions up to n = 991, whose x has 30 digits.
8. **Exact series.** Bernoulli numbers, exact harmonic numbers, and Faulhaber's formula checked against a direct sum.

```
n = 61   x = 1766319049,  y = 226153980
7        0.(142857)        period 6   order of 10: 6   primitive root: 3
```

## Engineering demo

**The idea:** the ratio problems in electronics, answered with exact errors reported in parts per million (ppm).

Six scenes:

1. **UART crystals.** Five crystals against four baud rates. 11.0592 MHz gives zero error on all of them; 16 MHz is about 35,000 ppm out at 115200 baud.
2. **PLL clocks.** The multiplier and divider to make 12.288 MHz, 74.25 MHz, 48 MHz and 133.33 MHz from a 25 MHz reference, with the ladder of ever-better ratios.
3. **Sample-rate conversion.** Exact up and down factors, such as 160/147 for 44.1 kHz to 48 kHz.
4. **Resistors.** A 3.3 V divider and a regulator feedback network in several E-series, series and parallel combinations, and nearest standard values.
5. **Q15 fixed point.** Filter coefficients quantised with exact error, and why 0.1 has no finite binary form (`0.0(0011)`).
6. **Gear teeth.** The best ratio for π with a tooth limit on each wheel. 355:113 needs a 355-tooth wheel, so under that the best pair is 22:7.

```
Clock        9600 baud   19200 baud   38400 baud   115200 baud
11.0592 MHz    /72 (0)     /36 (0)      /18 (0)       /6 (0)
16 MHz    /104 (1602.6)  /52 (1602.6) /26 (1602.6)  /9 (-35493.8)
```

## Notes

- The finance and engineering demos print amounts and errors rounded for display only. The values behind them are exact fractions.
- The number-theory demo uses `BigInteger`'s own arithmetic for speed, as the library's `NumberTheory` class does. Factoring the RSA modulus takes about a second.
- The demos are meant to be read as well as run. Each scene is a short, self-contained method.

## License

Released under the MIT license, copyright 2026 Mario Gianota. See [LICENSE](LICENSE).

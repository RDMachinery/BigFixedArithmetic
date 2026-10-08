BigFixedArithmetic

BigFixedArithmetic is an arbitrary-precision Java fixed-point arithmetic library designed to eliminate integer overflow and floating-point representation errors. By backed scaled values with Java's BigInteger and executing calculations purely through soft-emulated bit shifts, addition, and subtraction, it provides a deterministic framework for applications where CPU floating-point rounding drift must be avoided.

What It Is For

Standard floating-point types (float, double) introduce representation errors and platform-dependent rounding behaviour, making them unsuitable for exact financial accounting or lockstep deterministic simulations. While primitive fixed-point implementations prevent floating-point drift, fixed 64-bit integer backings risk silent overflow.
BigFixedArithmetic bridges this gap by offering:
Arbitrary Precision: Scaled fractional precision can be configured to any target decimal depth.
Overflow Immunity: Uses BigInteger as the underlying register, eliminating bit-width caps.
Hardware Independence: Performs arithmetic without relying on native multiplication (*), division (/), or modulo (%) operators on the BigInteger values.   

How It Works

The class converts real numbers into scaled integers according to a fixed precision factor (SCALE=10 PRECISION ):   
Internal Value=Real Value×SCALE
All standard operations are emulated using fundamental bitwise and additive logic:   
Addition & Subtraction: Executed via direct BigInteger addition and subtraction.
Multiplication: Implements the Russian Peasant (binary shift-and-add) algorithm to multiply intermediate integer and fractional components.   
Division: Implements binary shift-and-subtract long division to compute quotient and fractional remainders without relying on native CPU division loops.   

Requirements & Compilation

Prerequisites
Java Development Kit (JDK) 8 or higher.

Compilation
Save the code in a file named BigFixedArithmetic.java and compile it using javac:

javac BigFixedArithmetic.java

Basic Usage

Running the Included Demo

You can run the compiled class directly to execute the included main demonstration:
Bash
java BigFixedArithmetic

Code Example

Below is a quick example showing how to initialise numbers from strings or long integers and perform basic arithmetic:

public class Main {
    public static void main(String[] args) {
        // Initialise values from Strings or long integers
        BigFixedArithmetic a = BigFixedArithmetic.of("123456789.987654321");
        BigFixedArithmetic b = BigFixedArithmetic.of("0.333333333333333333");
        BigFixedArithmetic c = BigFixedArithmetic.of(100);

        // Perform arithmetic operations
        BigFixedArithmetic sum        = a.add(b);
        BigFixedArithmetic difference = a.subtract(b);
        BigFixedArithmetic product    = a.multiply(b);
        BigFixedArithmetic quotient   = a.divide(b);

        // Display results
        System.out.println("a + b = " + sum);
        System.out.println("a - b = " + difference);
        System.out.println("a * b = " + product);
        System.out.println("a / b = " + quotient);
    }
}

License

This project is open-source and available under the MIT License.

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        // ArithmeticCompiler.result("2 ^ 6 * 2 + 2 - 10 ^ 3");

        Compiler compiler = new Compiler();

        compiler.compile("test.script", "test.aroll");

        Assembler.assemble("test.aroll", "test.roll");
    }
}
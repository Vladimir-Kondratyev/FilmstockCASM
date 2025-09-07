import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Main {

    public static void testMain(String[] args) throws IOException {
        Compiler compiler = new Compiler();
        compiler.compile("test.script", "test.aroll");
        Assembler.assemble("test.aroll", "test.roll");
    }

    public static void compile(String inPath, String outPath) throws IOException {
        Compiler compiler = new Compiler();
        compiler.compile(inPath, outPath);
    }

    public static void assemble(String inPath, String outPath) throws IOException {
        Assembler.assemble(inPath, outPath);
    }

    public static void build(String inPath, String outPath) throws IOException {
        // temp file path that is unlikely to collide
        Path tempFile = Files.createTempFile("temp_build_", ".aroll");
        try {
            compile(inPath, tempFile.toString());
            assemble(tempFile.toString(), outPath);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No arguments provided.");
            return;
        }

        try {
            switch (args[0]) {
                case "-test":
                    testMain(Arrays.copyOfRange(args, 1, args.length));
                    break;

                case "-build":
                    if (args.length != 3) {
                        System.err.println("Usage: -build <inpath> <outpath>");
                        return;
                    }
                    build(args[1], args[2]);
                    break;

                case "-assemble":
                    if (args.length != 3) {
                        System.err.println("Usage: -assemble <inpath> <outpath>");
                        return;
                    }
                    assemble(args[1], args[2]);
                    break;

                case "-compile":
                    if (args.length != 3) {
                        System.err.println("Usage: -compile <inpath> <outpath>");
                        return;
                    }
                    compile(args[1], args[2]);
                    break;

                default:
                    System.err.println("Unknown command: " + args[0]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

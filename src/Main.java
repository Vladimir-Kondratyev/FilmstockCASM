import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class Main {
    public static boolean debug = false;


    public static void testMain(String[] args) throws IOException {
        Compiler compiler = new Compiler();
        compiler.compile("test.fss", "test.aroll");
        Assembler.assemble("test.aroll", "test.filmstock");
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

    static void printHelp() {
        System.out.println("You can use multiple commands after one another to queue tasks.");
        System.out.println("-help: Prints this.");
        System.out.println("-build    <inpath> <outpath> : Compiles and assembles a program.");
        System.out.println("-compile  <inpath> <outpath> : Compiles Filmstock into Filmstock Assembly.");
        System.out.println("-assemble <inpath> <outpath> : Assembles Filmstock Assembly into bytecode.");
        System.out.println("-build    <inpath> <outpath> : Compiles and assembles a program.");
        System.out.printf("-O         <0-%d / help>      : Sets the optimization level or prints information about optimizations.\n",
                Compiler.maxOptimizations);
        System.out.println("-test                        : Compiles test.fss to 'test.aroll' and assembles it into 'test.filmstock'.");
        System.out.println("-v                           : Verbose: When compiling, you can use this flag to " +
                "debug a lot of info about the next Compilation / Assembly.");
        System.out.println("                            -> Used for quick debugging.");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No arguments provided.");
            printHelp();
            return;
        }

        int lastStatement = 0;

        while (lastStatement < args.length) {
            try {
                switch (args[lastStatement]) {
                    case "-test":
                        testMain(Arrays.copyOfRange(args, 1, args.length));
                        lastStatement++;
                        debug = false;
                        break;

                    case "-build":
                        if (args.length - lastStatement < 3) {
                            System.err.println("Usage: -build <inpath> <outpath>");
                            return;
                        }
                        build(args[1+lastStatement], args[2+lastStatement]);
                        lastStatement += 3;
                        debug = false;
                        break;

                    case "-o", "-O":
                        if (args.length - lastStatement < 2) {
                            System.err.println("Usage: -O <0-%d / help> ");
                            return;
                        }

                        if (args[1+lastStatement].toLowerCase().contains("help")) {

                        }
                        else {
                            int level;
                            try {
                                level = Integer.parseInt(args[1 + lastStatement]);
                            } catch (NumberFormatException e) {
                                System.err.println("Could not parse optimization level!");

                                throw new RuntimeException(e);
                            }

                            Compiler.optimizationLevel = level;
                        }

                        lastStatement += 2;

                        break;

                    case "-assemble":
                        if (args.length - lastStatement < 3) {
                            System.err.println("Usage: -assemble <inpath> <outpath>");
                            return;
                        }
                        assemble(args[1+lastStatement], args[2+lastStatement]);
                        lastStatement += 3;
                        debug = false;
                        break;

                    case "-compile":
                        if (args.length - lastStatement < 3) {
                            System.err.println("Usage: -compile <inpath> <outpath>");
                            return;
                        }
                        compile(args[1+lastStatement], args[2+lastStatement]);
                        lastStatement += 3;
                        debug = false;
                        break;

                    case "-help":
                        lastStatement++;
                        printHelp();
                        break;

                    case "-debug":
                        debug = true;
                        lastStatement++;
                        break;

                    default:
                        System.err.println("Unknown argument: " + args[0]);
                        lastStatement++;
                        printHelp();
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

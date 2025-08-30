import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Assembler {
    public static String[] OPERATIONS = {
            "copy",         // 0
            "add",          // 1
            "subtract",     // 2
            "multiply",     // 3
            "divide",       // 4
            "squareRoot",   // 5
            "sin",          // 6
            "cos",          // 7
            "tan",          // 8
            "asin",         // 9
            "acos",         // 10
            "atan2",        // 11
            "pow",          // 12
            "mod",          // 13
            "equal",        // 14
            "greaterThan",  // 15
            "not",        // 16
            "and",        // 17
            "or",         // 18
            "jumpIf",       // 19
            "print",        // 20
            "printNumbers", // 21
            "iterate",       // 22
            "copyFrom",       // 23
            "pointer",       // 24
            "position",       // 25
            "jump"           // 26
    };

    public static final int[] OPERATION_LENGTHS = {
            2,  // copy(p1, p2)
            3,  // add(p1, p2, p3)
            3,  // subtract(p1, p2, p3)
            3,  // multiply(p1, p2, p3)
            3,  // divide(p1, p2, p3)
            2,  // squareRoot(p1, p2)
            2,  // sin(p1, p2)
            2,  // cos(p1, p2)
            2,  // tan(p1, p2)
            2,  // asin(p1, p2)
            2,  // acos(p1, p2)
            3,  // atan2(p1, p2, p3)
            3,  // pow(p1, p2, p3)
            3,  // mod(p1, p2, p3)
            3,  // equal(p1, p2, p3)
            3,  // compare(p1, p2, p3)
            2,  // notOp(p1, p2)
            3,  // andOp(p1, p2, p3)
            3,  // orOp(p1, p2, p3)
            2,  // jumpIf(p1, p2)
            2,  // print(p1, p2)
            2,  // printNumbers(p1, p2)
            1,  // iterate(p1)
            2,  // copyFrom(p1, p2)
            2,  // pointer(p1, p2)
            1,  // position(p1)
            1   // jump(p1)
    };

    public static void assemble(String assemblyPath, String outputPath) throws IOException {
        writeBytesToFile(Assembler.toBytes(Assembler.getParts(Assembler.readFileClean(assemblyPath))), outputPath);
        System.out.println("Assembled!");
    }

    public static void writeBytesToFile(byte[] data, String filename) throws IOException {
        try (FileOutputStream out = new FileOutputStream(filename)) {
            for (byte b : data) {
                out.write(b & 0xFF);
            }

            int[] printData = new int[data.length];
            for (int i = 0; i < data.length; i++) {
                printData[i] = data[i] & 0xFF;
            }
        }
    }

    static int margin = 64;

    public static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }

    public static byte[] doubleToBytes(double value) {
        return ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(value)
                .array();
    }


    public static String[] readFileClean(String path) {
        String filePath = path;

        List<String> clean = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                line = line.split("//")[0];

                if (line.length() > 0)
                    clean.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return clean.toArray(new String[0]);
    }

    public static String[][] getParts(String[] input) {
        String[][] separated = new String[3][];

        // Generate Header
        if (!input[0].contains("length"))
            throw new AssemblingException("Couldn't find length header!");

        separated[0] = new String[1];
        separated[0][0] = input[0];

        // Generate setting lines
        int lastSetLine = 0;
        for (int i = 1; i < input.length; i++) {
            if (input[i].contains("set"))
                lastSetLine = i;
            else break;
        }

        separated[1] = new String[lastSetLine];

        if (lastSetLine != 0){
            // Safety checks
            for (int i = 1; i < lastSetLine + 1; i++) {
                if (!input[i].contains("set"))
                    throw new AssemblingException("Set statements are mixed with other statements: " + input[i]);
            }

            for (int i = lastSetLine+1; i < input.length; i++) {
                if (input[i].contains("set"))
                    throw new AssemblingException("Set statements are mixed with other statements: " + input[i]);
            }

            for (int i = 1; i < lastSetLine + 1; i++) {
                separated[1][i-1] = input[i];
            }
        }

        separated[2] = new String[input.length - lastSetLine - 1];
        for (int i = lastSetLine+1; i < input.length; i++) {
            separated[2][i-lastSetLine-1] = input[i];
        }

        return separated;
    }

    private record SetInstruction(int index, double value){};

    public static byte[] toBytes(String[][] parts){
        List<byte[]> bytes = new ArrayList<>();

        bytes.add(intToBytes(Integer.parseInt(digits(parts[0][0]))));

        List<SetInstruction> setInstructions = new ArrayList<>();
        List<Integer> registers = new ArrayList<>();
        // Parse set instructions.
        for (String setInstruction : parts[1]){
            String[] p = setInstruction.split(" ");
            if (p.length < 3)
                throw new AssemblingException("Invalid set call: " + setInstruction);

            int index = Integer.parseInt(p[1]);
            double value = Double.parseDouble(p[2]);

            setInstructions.add(new SetInstruction(index, value));
            if (!registers.contains(index))
                registers.add(index);
        }

        // Only take the latest set statement to avoid tampering with the margins
        for (int register : registers) {
            for (int i = setInstructions.size()-1; i > -1 ; i--) {
                SetInstruction instruction = setInstructions.get(i);

                if (instruction.index == register){
                    bytes.add(intToBytes(instruction.index()));
                    bytes.add(doubleToBytes(instruction.value()));
                    break;
                }
            }
        }

        // Add margin
        for (int i = 0; i < margin; i++) {
            bytes.add(new byte[] {-1});
        }

        // Parse instructions.
        for (String instuction : parts[2]){
            String[] p = instuction.split(" ");
            int operationType = -1;

            for (int i = 0; i < OPERATIONS.length; i++) {
                if (Objects.equals(p[0], OPERATIONS[i])) {
                    operationType = i;
                    break;
                }
            }

            if (operationType == -1)
                throw new AssemblingException("Unknown instruction: " + instuction);

            if (p.length != OPERATION_LENGTHS[operationType] + 1)
                throw new AssemblingException("Invalid arguments amount: " + instuction);

            bytes.add(intToBytes(operationType));

            int[] arguments = new int[] {0, 0, 0};

            for (int i = 1; i < p.length; i++) {
                arguments[i-1] = Integer.parseInt(p[i]);
            }

            for (int argument : arguments) {
                bytes.add(intToBytes(argument));
            }
        }

        int length = 0;
        for (byte[] value : bytes) {
            length += value.length;
        }

        byte[] result = new byte[length];

        int latest = 0;
        for (byte[] aByte : bytes) {
            for (byte b : aByte) {
                result[latest] = b;
                latest += 1;
            }
        }

        return result;
    }

    public static String digits(String input){
        return input.replaceAll("[^0-9.]", "");
    }
}

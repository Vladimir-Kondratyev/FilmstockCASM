import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Compiler {
    int length = 0;

    int currentTemporaryVar = 0;
    private List<Variable> temporaryVariables = new ArrayList<>();

    private Variable getTemporary() {
        currentTemporaryVar++;
        if (currentTemporaryVar > temporaryVariables.size()) {
            Variable newVar = new Variable("Temp: " + currentTemporaryVar);
            temporaryVariables.add(newVar);
            return newVar;
        }
        else {
            return temporaryVariables.get(currentTemporaryVar-1);
        }
    }

    public static String[] readFileClean(String path) {
        String filePath = path;

        List<String> clean = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                line = line.split("//")[0];

                if (!line.isEmpty())
                    clean.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return clean.toArray(new String[0]);
    }

    public List<AssemblyOperation> compileLine(String input, int line) {
        Variable returnVar = null;

        System.out.printf("Compiling line %d: %s.%n", line, input);

        // If this is a = statement, separate :)
        String cInput = input;
        if (input.contains("=")) {
            String[] separated = input.split("=");
            separated[0] = separated[0].replace(" ", "");

            if (doesVarExist(separated[0]))
                returnVar = get(separated[0]);
            else
                throw new CompilationException(String.format("Line: %d%nVariable %s does not exist!", line, separated[0]));
            cInput = separated[1].replace(" ", "");
        }

        // Compile the whole into a tree.
        Node node;
        try {
            node = ArithmeticCompiler.compile(this, cInput);
        } catch (Exception e) {
            throw new CompilationException(String.format("Line: %d%n%s", line, e.getMessage()));
        }

        System.out.println(node + "\n");

        // Compile root into assembly.
        List<AssemblyOperation> operations;
        try {
            operations = this.compileRoot(node, returnVar);
        } catch (Exception e) {
            throw new CompilationException(String.format("Line: %d%n%s", line, e.getMessage()));
        }

        return operations;
    }

    public void compile(String inPath, String outPath) {
        List<AssemblyOperation> operations = new ArrayList<>();
        String[] clean = readFileClean(inPath);

        // Allocate variables.
        for (int i = 0; i < clean.length; i++) {
            if (clean[i].startsWith("var ")) {
                clean[i] = clean[i].substring(4);
                get(clean[i].split("=")[0].replace(" ", ""));
            }
        }

        System.out.println(variables);

        for (String line : clean) {
            dropTemporary();
            operations.addAll(compileLine(line, 0));
        }

        updateMemoryRegs();

        StringBuilder builder = new StringBuilder();

        builder.append("length = ").append(length).append("\n");

        for (int i = 0; i < constants.size(); i++) {
            builder.append("set ").
                    append(constants.get(i).memoryPosition).
                    append(" ").append(constantValues.get(i)).
                    append("\n");
        }

        for (AssemblyOperation operation : operations) {
            builder.append(operation.toString()).append("\n");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outPath))) {
            writer.write(builder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static final String[] BUILT_IN_FUNCTION_NAMES = new String[] {
            "sin",
            "cos",
            "tan",
            "asin",
            "acos",
            "atan2",
            "root",
            "not",
            "it", // Iterate
            "pointer",
            "position",
            "sqrt",
            "print",
            "ascii",
            "ia" // Iterate After
    };

    public static final int[] BUILT_IN_FUNCTION_ARG_AMOUNT = new int[] {
            1, // "sin",
            1, // "cos",
            1, // "tan",
            1, // "asin",
            1, // "acos",
            2, // "atan2",
            2, // "root",
            1, // "not",
            1, // "it", // Iterate
            1, // "pointer",
            0, // "position"
            1, // "sqrt"
            -1,// "print" = special case
            -1,// "ascii" = special case
            1  // "ia" // Iterate After
    };

    public List<String> functionNames = new ArrayList<>();
    public List<Integer> functionArgumentAmounts = new ArrayList<>();

    public Node nodeFromData(String operation, List<Node> children) {
        Node node = new Node(new NodeValue(OPType.fromString(operation)));

        node.children = children;

        return node;
    }

    public Node nodeFromData(List<Node> children, String function) {
        Node node = new Node(new NodeValue(OPType.FUNCTION_CALL));

        node.function = function;

        node.children = children;

        return node;
    }

    public Node nodeFromData(Variable variable) {
        return new Node(new NodeValue(variable));
    }

    private void dropTemporary() {
        currentTemporaryVar = 0;
    }

    List<Variable> variables = new ArrayList<>();
    List<String> names = new ArrayList<>();

    public Variable get(String name) {
        for (int i = 0; i < variables.size(); i++) {
            if (Objects.equals(names.get(i), name))
                return variables.get(i);
        }

        Variable newVar = new Variable(name);

        variables.add(newVar);
        names.add(name);

        return newVar;
    }

    public boolean doesVarExist(String name){
        for (int i = 0; i < variables.size(); i++) {
            if (Objects.equals(names.get(i), name))
                return true;
        }
        return false;
    }

    List<Variable> constants = new ArrayList<>();
    List<Double> constantValues = new ArrayList<>();

    public Variable getConstant(double value) {
        for (int i = 0; i < constants.size(); i++) {
            if (constantValues.get(i) == value)
                return constants.get(i);
        }

        Variable newVar = new Variable(Double.toString(value));

        constants.add(newVar);
        constantValues.add(value);

        return newVar;
    }

    private void updateMemoryRegs() {
        int currentId = 0;
        for (Variable temp : temporaryVariables)
            temp.memoryPosition = currentId++;

        System.out.printf("%d temporary variables.%n", currentId);

        for (Variable var : constants) {
            var.memoryPosition = currentId++;
        }

        for (Variable var : variables) {
            var.memoryPosition = currentId++;
        }

        length = currentId;

        System.out.println("\nConstants:");
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("%f : %d%n", constantValues.get(i), constants.get(i).memoryPosition);
        }

        System.out.println("\nVariables:");
        for (int i = 0; i < variables.size(); i++) {
            System.out.printf("%s : %d%n", names.get(i), variables.get(i).memoryPosition);
        }
    }

    // Not the real main, just test
    public static void main(String[] args) {
        Compiler compiler = new Compiler();

    }


    public class Variable {
        int memoryPosition;
        final String debugName;

        public Variable(String debugName) {
            this.debugName = debugName;
        }

        @Override
        public String toString() {
            return "Variable: " + debugName;
        }
    }

    public enum Type {
        COPY,
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MOD,
        POWER,
        SIN,
        COS,
        TAN,
        ASIN,
        ACOS,
        ATAN2,
        IS_EQUAL,
        IS_GREATER,
        NOT,
        AND,
        OR,
        JUMP_IF,
        PRINT,
        PRINT_NUMBERS,
        ITERATE,
        COPY_FROM,
        POINTER,
        POSITION,
        JUMP
    }

    private int expectedArguments(OPType t) {
        return switch (t) {
            case POWER, MULTIPLY, DIVIDE, MOD, ADD, SUBTRACT, AND, OR, IS_EQUAL, IS_GREATER, IS_SMALLER -> 2;
            case FUNCTION_CALL, BUILT_IN_FUNC_CALL -> -1;
        };
    }

    public enum OPType {
        POWER("^"),
        MULTIPLY("*"),
        DIVIDE("/"),
        MOD("%"),
        ADD("+"),
        SUBTRACT("-"),
        AND("&"),
        OR("|"),
        IS_EQUAL("?"),
        IS_GREATER(">"),
        IS_SMALLER("<"),
        FUNCTION_CALL("\0"),
        BUILT_IN_FUNC_CALL("\0");

        private final String symbol;

        OPType(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        public static OPType fromString(String s) {
            for (OPType op : OPType.values()) {
                if (op.symbol.equals(s)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown operation: " + s);
        }
    }


    private String typeToString(Type type) {
        switch (type) {
            case COPY -> {
                return "copy";
            }
            case ADD -> {
                return "add";
            }
            case SUBTRACT -> {
                return "subtract";
            }
            case MULTIPLY -> {
                return "multiply";
            }
            case DIVIDE -> {
                return "divide";
            }
            case MOD -> {
                return "mod";
            }
            case POWER -> {
                return "pow";
            }
            case SIN -> {
                return "sin";
            }
            case COS -> {
                return "cos";
            }
            case TAN -> {
                return "tan";
            }
            case ASIN -> {
                return "asin";
            }
            case ACOS -> {
                return "acos";
            }
            case ATAN2 -> {
                return "atan2";
            }
            case IS_EQUAL -> {
                return "equal";
            }
            case IS_GREATER -> {
                return "greaterThan";
            }
            case NOT -> {
                return "not";
            }
            case AND -> {
                return "and";
            }
            case OR -> {
                return "or";
            }
            case JUMP_IF -> {
                return "jumpIf";
            }
            case PRINT -> {
                return "print";
            }
            case PRINT_NUMBERS -> {
                return "printNumbers";
            }
            case ITERATE -> {
                return "iterate";
            }
            case COPY_FROM -> {
                return "copyFrom";
            }
            case POINTER -> {
                return "pointer";
            }
            case POSITION -> {
                return "position";
            }
            case JUMP -> {
                return "jump";
            }
        }
        throw new CompilationException("Unknown operation type :(, " + type);
    }



    class AssemblyOperation {
        Type type;
        Variable[] vars;

        public String header = null;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            builder.append(typeToString(type));

           for (Variable variable : vars)
               builder.append(" ").append(variable.memoryPosition);

           if ((type == Type.PRINT || type == Type.PRINT_NUMBERS) && vars.length == 1)
               builder.append(" 1");

           return builder.toString();
        }

        public AssemblyOperation(Type type, Variable[] vars) {
            this.type = type;
            this.vars = vars;
        }
    }

    public class NodeValue{
        private Variable variable;
        private OPType opType;
        private boolean isVariable;
        public boolean doCopy = true;

        public NodeValue(OPType opType){
            isVariable = false;
            this.opType = opType;
        }

        public NodeValue(Variable variable){
            isVariable = true;
            this.variable = variable;
        }

        public Variable getVariable() {
            return variable;
        }

        public void setVariable(Variable variable) {
            this.variable = variable;
            opType = null;

            isVariable = true;
        }

        public OPType getOpType() {
            return opType;
        }

        public void setOpType(OPType opType) {
            this.opType = opType;
            variable = null;

            isVariable = false;
        }

        public boolean isVariable() {
            return isVariable;
        }
    }

    public class Node {
        NodeValue value;
        List<Node> children = new ArrayList<>();

        public String function;

        Node(NodeValue value) {
            this.value = value;
        }

        void addChild(Node child) {
            children.add(child);
        }

        @Override
        public String toString() {
            return toString("", true);
        }

        private String toString(String prefix, boolean isTail) {
            StringBuilder sb = new StringBuilder();

            // Print this node's value
            if (value.isVariable()) {
                sb.append(prefix).append(isTail ? "└── " : "├── ").append(value.getVariable().toString()).append("\n");
            } else {
                if (value.opType != OPType.FUNCTION_CALL) {
                    sb.append(prefix).append(isTail ? "└── " : "├── ").append(value.getOpType().toString()).append("\n");
                }
                else {
                    sb.append(prefix).append(isTail ? "└── " : "├── ").append(value.getOpType().toString()).append(": ").append(function).append("\n");
                }
            }

            // Print children recursively
            for (int i = 0; i < children.size() - 1; i++) {
                sb.append(children.get(i).toString(prefix + (isTail ? "    " : "│   "), false));
            }
            if (children.size() > 0) {
                sb.append(children.get(children.size() - 1)
                        .toString(prefix + (isTail ? "    " : "│   "), true));
            }

            return sb.toString();
        }
    }

    private List<AssemblyOperation> compileRoot(Node root, Variable writeTo) {
        dropTemporary();
        List<AssemblyOperation> operations = compile(root);

        if (writeTo != null)
            operations.add(new AssemblyOperation(Type.COPY, new Variable[] {root.value.getVariable(), writeTo}));

        return operations;
    }

    private List<AssemblyOperation> compile(Node node) {
        List<AssemblyOperation> result = new ArrayList<>();

        if (node.value.isVariable()) {
            if (node.value.doCopy) {
                // Copy the variable into a temporary one
                Variable temp = getTemporary();
                result.add(new AssemblyOperation(Type.COPY, new Variable[] {node.value.getVariable(), temp}));
                node.value.setVariable(temp);
            }
            else {
                node.value.setVariable(node.value.getVariable());
            }
        }
        else {
            // Don't copy in iterations
            if (node.value.getOpType() == OPType.FUNCTION_CALL &&
                    (   Objects.equals(node.function, "it") ||
                    Objects.equals(node.function, "ia"))){
                node.children.get(0).value.doCopy = false;
            }

            // Execute and compile the instructions of the children.
            for (Node child : node.children)
                result.addAll(compile(child));


            switch (node.value.getOpType()){
                // Default operations with two inputs
                case POWER, MULTIPLY, DIVIDE, MOD, ADD, SUBTRACT, AND, OR, IS_EQUAL, IS_GREATER, IS_SMALLER:
                    if (node.children.size() != expectedArguments(OPType.ADD)) // Use add, cause all of these use 2.
                        throw new CompilationException("Adding more then two numbers together!");
                    Variable a = node.children.get(0).value.getVariable();
                    Variable b = node.children.get(1).value.getVariable();

                    // Is smaller is just a flipped is bigger :)
                    if (node.value.opType == OPType.IS_SMALLER)
                        result.add(new AssemblyOperation(fromOP(node.value.getOpType()), new Variable[] {b, a, a}));
                    else
                        result.add(new AssemblyOperation(fromOP(node.value.getOpType()), new Variable[] {a, b, a}));

                    node.value.setVariable(a);

                    break;

                case FUNCTION_CALL:
                    String functionName = node.function;

                    int builtInFunction = -1;
                    for (int i = 0; i < BUILT_IN_FUNCTION_NAMES.length; i++) {
                        if (Objects.equals(functionName, BUILT_IN_FUNCTION_NAMES[i])){
                            builtInFunction = i;
                            break;
                        }
                    }

                    if (builtInFunction != -1) {
                        Variable[] arguments = new Variable[node.children.size()];

                        for (int i = 0; i < arguments.length; i++) {
                            arguments[i] = node.children.get(i).value.getVariable();
                        }

                        // Check if the amount of arguments is correct.
                        if (BUILT_IN_FUNCTION_ARG_AMOUNT[builtInFunction] != -1
                                && BUILT_IN_FUNCTION_ARG_AMOUNT[builtInFunction] != arguments.length)
                            throw new CompilationException(String.format("Expected %d but got %d arguments for the built-in function %s!",
                                    BUILT_IN_FUNCTION_ARG_AMOUNT[builtInFunction], arguments.length, functionName));

                        result.addAll(getAssemblyOfBuiltIn(builtInFunction, (arguments.length == 0) ? new Variable[] {null} : arguments));

                        System.out.println(result);

                        node.value.setVariable(arguments[0]); // 0 is the return field for built-in functions.
                    }
                    else
                        throw new CompilationException(String.format("Unknown function %s!%n", functionName));

                    break;
                default:
                    throw new CompilationException("Unknown Operation!");
            }
        }

        return result;
    }

    public List<AssemblyOperation> getAssemblyOfBuiltIn(int functionId, Variable[] arguments) {
        List<AssemblyOperation> result = new ArrayList<>();

        switch (functionId){
            case 0, 1, 2, 3, 4, 7: // sin, cos, tan, asin, acos, not
                result.add(new AssemblyOperation(fromBuiltInId(functionId), new Variable[] {arguments[0], arguments[0]}));
                break;
            case 5: // atan2
                result.add(new AssemblyOperation(fromBuiltInId(functionId), new Variable[] {arguments[0], arguments[1], arguments[0]}));
                break;
            case 6: // root
                // Copy 1 into a temp
                Variable temp = getTemporary();
                result.add(new AssemblyOperation(Type.COPY, new Variable[] {getConstant(1), temp}));
                // Get 1 / arguments[1] and save it back to the temp
                result.add(new AssemblyOperation(Type.DIVIDE, new Variable[] {temp, arguments[1], temp}));

                // Raise arguments[0] to (1/arguments[1]) and save back to the arguments[0]
                result.add(new AssemblyOperation(Type.POWER, new Variable[] {arguments[0], temp, arguments[0]}));
                break;
            case 8: // Iterate
                Variable tempIterationReturn = getTemporary();
                result.add(new AssemblyOperation(Type.ITERATE, new Variable[] {arguments[0]}));

                result.add(new AssemblyOperation(Type.COPY, new Variable[] {arguments[0], tempIterationReturn}));

                arguments[0] = tempIterationReturn;
                break;
            case 9:
                throw new CompilationException("Usage of discontinued pointer function!");
            case 10: // Position
                throw new CompilationException("Usage of discontinued position function!");
            case 11:
                result.add(new AssemblyOperation(Type.POWER, new Variable[] {arguments[0], getConstant(0.5), arguments[0]}));
                break;
            case 12:
                // Print the numbers separated with ", " and ending with a new line.
                for (int i = 0; i < arguments.length; i++) {
                    result.add(new AssemblyOperation(Type.PRINT_NUMBERS, new Variable[] {arguments[i]}));
                    if (i < arguments.length - 1) {
                        result.add(new AssemblyOperation(Type.PRINT, new Variable[]{getConstant(ASCII.COMMA)}));
                        result.add(new AssemblyOperation(Type.PRINT, new Variable[]{getConstant(ASCII.SPACE)}));
                    }
                }
                result.add(new AssemblyOperation(Type.PRINT, new Variable[]{getConstant(ASCII.NEW_LINE)}));
                break;
            case 13:
                // Print the chars and ending with a new line.
                for (Variable argument : arguments)
                    result.add(new AssemblyOperation(Type.PRINT, new Variable[]{argument}));
                result.add(new AssemblyOperation(Type.PRINT, new Variable[]{getConstant(ASCII.NEW_LINE)}));
                break;
            case 14: // Iterate After
                Variable tempIterationAfterReturn = getTemporary();
                Variable iterateAfter = arguments[0];

                result.add(new AssemblyOperation(Type.COPY, new Variable[] {arguments[0], tempIterationAfterReturn}));
                result.add(new AssemblyOperation(Type.ITERATE, new Variable[] {iterateAfter}));

                arguments[0] = tempIterationAfterReturn;
                break;
            default:
                throw new CompilationException("Unknown built-in function with id: " + functionId);
        }

        return result;
    }

    public Type fromBuiltInId(int id) {
        return switch (id) {
            case 0 -> Type.SIN;
            case 1 -> Type.COS;
            case 2 -> Type.TAN;
            case 3 -> Type.ASIN;
            case 4 -> Type.ACOS;
            case 5 -> Type.ATAN2;
            case 6 -> Type.POWER;  // "root"
            case 7 -> Type.NOT;
            case 8 -> Type.ITERATE;     // "it"
            case 9 -> throw new CompilationException("Usage of discontinued pointer function!");
            case 10 -> Type.POSITION;
            case 11 -> Type.POWER; // "sqrt"
            case 12 -> Type.PRINT_NUMBERS;
            case 13 -> Type.PRINT; // "ascii"
            default -> throw new CompilationException("Unknown built in function id: " + id);
        };
    }


    public Type fromOP(OPType opType) {
        return switch (opType) {
            case POWER     -> Type.POWER;
            case MULTIPLY  -> Type.MULTIPLY;
            case DIVIDE    -> Type.DIVIDE;
            case MOD       -> Type.MOD;
            case ADD       -> Type.ADD;
            case SUBTRACT  -> Type.SUBTRACT;
            case AND       -> Type.AND;
            case OR        -> Type.OR;
            case IS_EQUAL  -> Type.IS_EQUAL;
            case IS_GREATER, IS_SMALLER -> Type.IS_GREATER;
            default        -> throw new IllegalArgumentException("Unsupported OPType: " + opType);
        };
    }

}
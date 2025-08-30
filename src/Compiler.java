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

    public List<String> functionNames = new ArrayList<>();

    public Node nodeFromData(String operation, List<Node> children) {
        Node node = new Node(new NodeValue(OPType.fromString(operation)));

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

        for (Variable var : variables) {
            var.memoryPosition = currentId++;
        }

        for (int i = 0; i < variables.size(); i++) {
            System.out.printf("%s : %d%n", names.get(i), variables.get(i).memoryPosition);
        }
    }

    public static void main(String[] args) {
        Compiler compiler = new Compiler();

        // Get variables x, y, z
        Variable x = compiler.get("x");
        Variable y = compiler.get("y");
        Variable z = compiler.get("z");

        // Build tree: (x + y) + z
        Node addXY = compiler.new Node(compiler.new NodeValue(OPType.ADD));
        addXY.addChild(compiler.new Node(compiler.new NodeValue(x)));
        addXY.addChild(compiler.new Node(compiler.new NodeValue(y)));

        Node addXYZ = compiler.new Node(compiler.new NodeValue(OPType.ADD));
        addXYZ.addChild(addXY);
        addXYZ.addChild(compiler.new Node(compiler.new NodeValue(z)));

        // Create a result variable
        Variable result = compiler.get("result");

        // Compile root node
        List<AssemblyOperation> operations = compiler.compileRoot(addXYZ, result);

        // Assign memory positions for all vars
        compiler.updateMemoryRegs();

        // Print assembly
        for (AssemblyOperation op : operations) {
            System.out.println(op);
        }
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
        ADD
    }

    private int expectedArguments(OPType t) {
        return switch (t) {
            case POWER, MULTIPLY, DIVIDE, MOD, ADD, SUBTRACT, AND, OR, IS_EQUAL, IS_GREATER, IS_SMALLER -> 2;
            case FUNCTION_CALL -> -1;
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
        FUNCTION_CALL("\0");

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


    private String typeToString(Type type){
        switch (type) {
            case COPY -> {
                return "copy";
            }
            case ADD -> {
                return "add";
            }
        }
        throw new CompilationException("Unknown operation type :(, " + type);
    }

    class AssemblyOperation {
        Type type;
        Variable[] vars;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            builder.append(typeToString(type));

           for (Variable variable : vars)
               builder.append(" ").append(variable.memoryPosition);

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
                sb.append(prefix).append(isTail ? "└── " : "├── ").append(value.getOpType().toString()).append("\n");
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

        operations.add(new AssemblyOperation(Type.COPY, new Variable[] {root.value.getVariable(), writeTo}));

        return operations;
    }

    private List<AssemblyOperation> compile(Node node) {
        List<AssemblyOperation> result = new ArrayList<>();

        if (node.value.isVariable()) {
            // Copy the variable into a temporary one
            Variable temp = getTemporary();
            result.add(new AssemblyOperation(Type.COPY, new Variable[] {node.value.getVariable(), temp}));
            node.value.setVariable(temp);
        }
        else {
            // Execute and compile the instructions of the children.
            for (Node child : node.children)
                result.addAll(compile(child));

            switch (node.value.getOpType()){
                case ADD:
                    if (node.children.size() != expectedArguments(OPType.ADD))
                        throw new CompilationException("Adding more then two numbers together!");

                    Variable a = node.children.get(0).value.getVariable();
                    Variable b = node.children.get(1).value.getVariable();
                    result.add(new AssemblyOperation(Type.ADD, new Variable[] {a, b, a}));

                    node.value.setVariable(a);

                    break;
            }
        }

        return result;
    }
}
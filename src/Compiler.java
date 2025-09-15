import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Compiler {
    private static final String FORBIDDEN_SYMBOLS = "_$#@=&|?><^*/+\\-%(){};,[]. ";
    private static final String NUMERIC = "1234567890.";
    // private static final String MACRO_FORBIDDEN_SYMBOLS = "@=&|?><^*/+\\-%(){};, ";

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

    int currentHeader = 0;
    private List<Variable> headers = new ArrayList<>();
    private List<Integer> headerOffsets = new ArrayList<>();

    private Variable getHeader(int offset) {
        Variable newVar = new Variable("Header: " + currentHeader);
        headers.add(newVar);
        headerOffsets.add(offset);
        currentHeader++;

        return newVar;
    }

    private int getLatestHeaderId() {
        return currentHeader-1;
    }

    private int getHeaderOffset(int id) {
        return headerOffsets.get(id);
    }

    List<List<String>> macros = new ArrayList<>();
    List<List<String>> macroArgumentNames = new ArrayList<>();
    List<String> macroNames = new ArrayList<>();

    public List<String> inlineMacro(String macroName, List<String> argumentNames) {
        int macroID = -1;

        for (int i = 0; i < macroNames.size(); i++) {
            if (Objects.equals(macroName, macroNames.get(i))) {
                macroID = i;
                break;
            }
        }
        if (macroID == -1)
            throw new CompilationException("Unknown macro!");
        
        List<String> macro = macros.get(macroID);
        List<String> macroArguments = macroArgumentNames.get(macroID);
        List<String> compiledMacro = new ArrayList<>(List.copyOf(macro));

        for (int l = 0; l < compiledMacro.size(); l++) {
            for (int arg = 0; arg < macroArguments.size(); arg++) {
                String line = compiledMacro.get(l);
                String argument = macroArguments.get(arg);

                int argumentLength = argument.length();

                List<Integer> beginnings = findIn(line, argument);

                // Validate all found indices.
                for (int b = beginnings.size() - 1; b>-1; b--) {
                    int beginning = beginnings.get(b);
                    boolean valid = isValidArgument(beginning, line, argumentLength);

                    if (!valid) {
                        beginnings.remove(b);
                    }
                }

                // Replace all valid macro arguments with the given Argument right to left
                for (int b = beginnings.size()-1; b > -1; b--) {
                    int beginning = beginnings.get(b);

                    line = line.substring(0, beginning-1) + argumentNames.get(arg)
                            + line.substring(beginning + argumentLength);
                }

                compiledMacro.set(l, line);
            }
            
        }

        String replace = macroName.toUpperCase() + "MACRO";
        for (int i = 0; i < compiledMacro.size(); i++) {
            compiledMacro.set(i, compiledMacro.get(i).replace("__", replace));
        }

        return compiledMacro;
    }

    private static boolean isValidArgument(int beginning, String line, int argumentLength) {
        boolean valid = true;

        // Check if the character before it is not an operation
        if (beginning > 0) {
            if (line.charAt(beginning-1)!='$') {
                valid = false;
            }
        }
        else {
            valid = false;
        }

        // Check if the character before it is not an operation
        if (beginning + argumentLength < line.length()) {
            if (!FORBIDDEN_SYMBOLS.contains(String.valueOf(line.charAt(beginning + argumentLength)))) {
                valid = false;
            }
        }
        return valid;
    }

    public void addMacro(String name, List<String> body, List<String> arguments) {
        macroNames.add(name);
        macros.add(body);
        macroArgumentNames.add(arguments);
    }

    public List<String> parseMacros(List<String> input){
        List<String> parsed = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            String line = input.get(i);

            if (line.startsWith("#macro")){
                String[] separated = line.split(" ");
                String joined = String.join("", Arrays.copyOfRange(separated, 1, separated.length));
                String[] joinedSeparated = joined.split("\\(");

                String name = joinedSeparated[0].replace(" ", "");

                String parsedArgs = joinedSeparated[1].split("}")[0].replace(" ", "");
                String[] arguments = parsedArgs.substring(0, parsedArgs.length()-1).split(",");

                if (arguments.length == 1) {
                    if (arguments[0].isEmpty()) {
                        arguments = new String[] {};
                    }
                }

                // Safety checks
                for (String argument : arguments){
                    if (argument.isEmpty())
                        throw new CompilationException("Empty argument in macro.");
                }
                if (name.isEmpty())
                    throw new CompilationException("Empty macro name.");

                int end = findEndOfStatement(i, input);

                List<String> body = input.subList(i+2, end);

                macros.add(body);
                macroNames.add(name);
                macroArgumentNames.add(List.of(arguments));

                i = end;
            }
            else {
                parsed.add(line);
            }
        }

        return parsed;
    }

    /**
     * Helper Method to find all beginnings of a substring in a string.
     * @param text Where to search?
     * @param pattern What to search for?
     * @return Beginning positions of the substring.
     */
    public static List<Integer> findIn(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        int index = text.indexOf(pattern);

        while (index >= 0) {
            positions.add(index);
            index = text.indexOf(pattern, index + pattern.length());
        }

        return positions;
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

    public List<AssemblyOperation> compileLine(String input, int line, Variable returnVar) {
        System.out.printf("Compiling line %d: %s.%n", line, input);

        // If this is a = statement, separate :)
        String cInput = input;
        if (input.contains("=")) {
            String[] separated = input.split("=");

            if (input.charAt(separated[0].length()-1) != '\\') {
                separated[0] = separated[0].replace(" ", "");

                if (doesVarExist(separated[0]))
                    returnVar = get(separated[0]);
                else
                    throw new CompilationException(String.format("Line: %d%nVariable %s does not exist!", line, separated[0]));
                cInput = separated[1].replace(" ", "");
            }
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

    public List<AssemblyOperation> parseIf (List<String> input, List<String> elseLines, AssemblyOperation breakStatement){
        List<AssemblyOperation> operations = new ArrayList<>();

        // Parse condition
        String[] separated = input.get(0).split(" ");
        String[] separatedWithoutIf = Arrays.copyOfRange(separated, 1, separated.length);
        String joinedNoIf = String.join("", separatedWithoutIf);

        String conditionString = joinedNoIf.split("\\{")[0];

        System.out.println("Condition:");
        System.out.println(conditionString);

        // Condition
        Variable condition = getTemporary();
        operations.addAll(compileLine(conditionString, 0, condition));

        // Create headers
        Variable ifBodyHeader = getHeader(1);
        int ifBodyHeaderId = getLatestHeaderId();

        Variable endHeader = getHeader(1);
        int endHeaderId = getLatestHeaderId();

        // Jump to header of the if body, if the condition was true
        operations.add(new AssemblyOperation(Type.JUMP_IF, new Variable[] {condition, ifBodyHeader}));

        // Add else Lines
        operations.addAll(compilePart(elseLines, null));
        operations.add(new AssemblyOperation(Type.JUMP, new Variable[] {endHeader}));

        // Add header to which we jump if the condition is true
        operations.get(operations.size()-1).headers.add(ifBodyHeaderId);

        List<String> stringsToExecute = input.subList(2, input.size()-1);
        operations.addAll(compilePart(stringsToExecute, breakStatement));

        // Add header to which we jump if the condition was false
        operations.get(operations.size()-1).headers.add(endHeaderId);
        return operations;
    }

    public List<AssemblyOperation> parseFor (List<String> input) {
        List<AssemblyOperation> operations = new ArrayList<>();

        String firstLine = input.get(0);
        String cleanFirst = firstLine.substring(3);

        cleanFirst = cleanFirst.replaceAll(";", " ; ");

        // 0 = Initialization
        // 1 = Condition
        // 2 = Update
        String[] forExpressions = cleanFirst.split(";");

        // Remove unnecessary white spaces (OMORI ahh)
        for (int i = 0; i < forExpressions.length; i++) {
            forExpressions[i] = forExpressions[i].strip();
        }

        // Initialize Headers
        Variable endHeader = getHeader(1);
        int endHeaderId = getLatestHeaderId();

        Variable endHeaderBreak = getHeader(1);
        int endHeaderBreakId = getLatestHeaderId();

        //#region Initialization
        // Initialize the variable if not already
        forExpressions[0] = allocate(forExpressions[0]);

        boolean isInitEmpty = forExpressions[0].isEmpty();

        Variable startHeader;
        int startHeaderId;
        if (!isInitEmpty) {
            startHeader = getHeader(1);
            startHeaderId = getLatestHeaderId();

            // Add initialization lines
            operations.addAll(compileLine(forExpressions[0], 0, null));

            // Add header after initialization
            operations.get(operations.size()-1).headers.add(startHeaderId);
        }
        else {
            startHeader = getHeader(0);
            startHeaderId = getLatestHeaderId();
        }
        //#endregion

        //#region Condition
        // Initialize condition
        Variable condition = getTemporary();

        // Get the reverse
        if (!forExpressions[1].isEmpty())
            forExpressions[1] = "not(" + forExpressions[1] + ")";
        else {
            forExpressions[1] = "0";
        }

        operations.addAll(compileLine(forExpressions[1], 0, condition));
        // Break operation is what is executed for "break" lines.
        AssemblyOperation breakOperation = new AssemblyOperation(Type.JUMP, new Variable[] {endHeaderBreak});

        operations.add(new AssemblyOperation(Type.JUMP_IF, new Variable[] {condition, endHeader}));

        if (isInitEmpty)
            operations.get(0).headers.add(startHeaderId);

        //#endregion

        // Add main part
        List<String> stringsToExecute = input.subList(2, input.size()-1);
        operations.addAll(compilePart(stringsToExecute, breakOperation));

        //#region Update
        if (!forExpressions[2].isEmpty())
            operations.addAll(compileLine(forExpressions[2], 0, null));
        //#endregion

        // Add jump to start
        operations.add(new AssemblyOperation(Type.JUMP, new Variable[] {startHeader}));

        // Add header to the end
        operations.get(operations.size()-1).headers.add(endHeaderId);
        operations.get(operations.size()-1).headers.add(endHeaderBreakId);

        return operations;
    }

    public List<AssemblyOperation> compilePart(List<String> clean, AssemblyOperation breakStatement) {
        List<AssemblyOperation> operations = new ArrayList<>();

        for (int i = 0; i < clean.size(); i++) {
            dropTemporary();
            if (clean.get(i).startsWith("if")) {
                int end = findEndOfStatement(i, clean);
                int elseEnd = end;

                if (end+1 < clean.size()){
                    if (clean.get(end+1).startsWith("else")) {
                        elseEnd = findEndOfStatement(end+1, clean);
                    }
                }

                List<String> elseLines = new ArrayList<>();
                if (elseEnd != end) {
                    elseLines = clean.subList(end+3,elseEnd);
                }
                operations.addAll(parseIf(clean.subList(i, end+1), elseLines, breakStatement));

                i = end + (elseEnd - end);
            }
            else if (clean.get(i).startsWith("for")){
                int end = findEndOfStatement(i, clean);

                operations.addAll(parseFor(clean.subList(i, end+1)));

                i = end;
            }
            else if (Objects.equals(clean.get(i), "break")){
                if (breakStatement == null)
                    throw new CompilationException("Using a break statement outside a loop!");
                else {
                    operations.add(breakStatement);
                }
            }
            else {
                operations.addAll(compileLine(clean.get(i), 0, null));
            }
        }

        return operations;
    }

    private int findEndOfStatement(int start, List<String> input) {
        int found = 0;
        int depth = 0;
        int end = -1;

        for (int j = start; j < input.size(); j++) {
            boolean doBreak = false;
            for (int c = 0; c < input.get(j).length(); c++) {
                if (input.get(j).charAt(c) == '{'){
                    found++;
                    depth++;
                }
                if (input.get(j).charAt(c) == '}'){
                    depth--;
                }

                if (found > 0 && depth == 0) {
                    end = j;
                    doBreak = true;

                    break;
                }
            }

            if (doBreak) break;
        }

        if (end == -1)
            throw new CompilationException("Unclosed statement!");

        return end;
    }

    private String allocate(String statement) {
        if (statement.startsWith("var ")) {
            String varString = statement.substring(3);
            get(varString.split("=")[0].replace(" ", ""));
            return statement.substring(4);
        }
        return statement;
    }

    private List<String> inlineMacros(List<String> input) {
        boolean changed = true;

        List<String> ret = new ArrayList<>(input);

        while (changed) {
            changed = false;
            List<String> output = new ArrayList<>();

            for (String line : ret) {
                if (line.startsWith("#")) {
                    changed = true;
                    String noHash = line.substring(1);

                    String[] separated = noHash.split("\\(");

                    String name = separated[0].replace(" ", "");
                    String joined = String.join("(", Arrays.copyOfRange(separated, 1, separated.length));

                    String parsedArgs = joined.replace(" ", "");
                    String[] arguments = parsedArgs.substring(0, parsedArgs.length()-1).split(",");

                    // System.out.println("Inline macros name: " + name);

                    output.addAll(inlineMacro(name, Arrays.asList(arguments)));
                }
                else {
                    output.add(line);
                }
            }

            ret = new ArrayList<>(output);
        }
        return ret;
    }

    private List<String> inlineFiles(List<String> input) {
        List<String> output = new ArrayList<>();

        for (String line : input) {
            if (line.startsWith("#")) {
                String noHash = line.substring(1);
                noHash = noHash.strip();

                if (noHash.startsWith("include")) {
                    String path = noHash.substring(7).strip();
                    List<String> loaded =List.of(readFileClean(path));

                    output.addAll(loaded);
                }
                else {
                    output.add(line);
                }
            }
            else {
                output.add(line);
            }
        }
        return output;
    }

    private List<String> setListSugar(List<String> input) {
        List<String> result = new ArrayList<>();

        for (String line : input) {
            String firstPart = line.split("=")[0];

            if (firstPart.contains("[") && line.contains("=")) {
                String[] seprarated = firstPart.split("\\[");
                seprarated[0] = seprarated[0].strip();
                seprarated[1] = String.join("[", Arrays.copyOfRange(seprarated, 1, seprarated.length)).strip();

                seprarated[1] = seprarated[1].substring(0, seprarated[1].length()-1);

                String out = "lset(" + seprarated[0] + ", " + seprarated[1]
                        + ", " + line.split("=")[1] + ")";

                result.add(out);
            }
            else {
                result.add(line);
            }
        }

        return result;
    }

    private List<String> getListSugar(List<String> input) {
        List<String> result = new ArrayList<>();

        for (String line : input) {
            String inline = line;

            while (true) {
                char[] charLine = inline.toCharArray();

                int start = -1;
                int end = -1;
                int d = 0;
                int startName = -1;

                for (int i = 0; i < charLine.length; i++) {
                    if (charLine[i] == '[') {
                        if (d==0) {
                            start = i;
                        }
                        d++;
                    }
                    else if (charLine[i] == ']') {
                        d--;
                        if (d==0) {
                            end = i;
                            break;
                        }
                    }
                }

                if (start != -1) {
                    for (int i = start-1; i > -1; i--) {
                        if (FORBIDDEN_SYMBOLS.contains(String.valueOf(charLine[i]))) {
                            startName = i + 1;
                            break;
                        }
                    }

                    String name = inline.substring(startName, start);
                    String body = inline.substring(start+1, end);

                    String r = "lget(" + name + ", " + body + ")";

                    inline = inline.substring(0, startName) + r + inline.substring(end + 1);
                }
                else {
                    result.add(inline);
                    break;
                }
            }
        }

        return result;
    }

    public List<String> functionListSugar(List<String> input) {
        List<String> result = new ArrayList<>();

        for (String line : input) {
            String inline = line;

            while (true) {
                char[] charLine = inline.toCharArray();

                int start = -1;
                int endFuncName = -1;
                int startName = -1;

                for (int i = 0; i < charLine.length; i++) {
                    if (i != 0) {
                        if (charLine[i] == '.' && (charLine[i-1] != '\\'
                                && !NUMERIC.contains(String.valueOf(charLine[i-1])))) {
                            start = i;
                            break;
                        }
                    }
                }

                if (start != -1) {
                    for (int i = start-1; i > -1; i--) {
                        if (FORBIDDEN_SYMBOLS.contains(String.valueOf(charLine[i]))) {
                            startName = i + 1;
                            break;
                        }
                        if (i == 0) {
                            startName = 0;
                            break;
                        }
                    }

                    for (int i = start+1; i < charLine.length; i++) {
                        if (FORBIDDEN_SYMBOLS.contains(String.valueOf(charLine[i]))) {
                            endFuncName = i;
                            break;
                        }
                    }

                    int d = 0;
                    int end = -1;

                    for (int i = endFuncName; i < charLine.length; i++) {
                        if (charLine[i] == '(') {
                            d++;
                        }
                        if (charLine[i] == ')') {
                            if (d == 1) {
                                end = i;
                                break;
                            }
                            d--;
                        }
                    }

                    String name = inline.substring(startName, start);
                    String funcName = inline.substring(start+1, endFuncName);
                    String arguments = inline.substring(endFuncName+1, end);

                    funcName = funcName.strip();
                    arguments = arguments.strip();
                    String r;

                    if (arguments.isEmpty()) {
                        r = "l" + funcName + "(" + name + ")";
                    }
                    else {
                        r = "l" + funcName + "(" + name + ", " + arguments + ")";
                    }

                    inline = inline.substring(0, startName) + r + inline.substring(end + 1);
                }
                else {
                    result.add(inline);
                    break;
                }
            }
        }


        return result;
    }

    public void compile(String inPath, String outPath) {
        List<String> clean = new ArrayList<>(Arrays.asList(readFileClean(inPath)));

        clean = inlineFiles(clean);

        clean = separateCurved(clean);

        clean = parseMacros(clean);

        clean = inlineMacros(clean);

        clean = setListSugar(clean);

        clean = getListSugar(clean);

        clean = functionListSugar(clean);

        // Debug Code
        System.out.println("Formatted code:");
        for (String line : clean) {
            System.out.println(line);
        }
        System.out.println();

        // Allocate variables.
        for (int i = 0; i < clean.size(); i++) {
            if (clean.get(i).startsWith("var ")) {
                clean.set(i, clean.get(i).substring(4));
                get(clean.get(i).split("=")[0].replace(" ", ""));
            }
            else if (clean.get(i).startsWith("for")) {
                String noFor = clean.get(i).substring(3);
                String firstPart = noFor.split(";")[0].strip();
                if (firstPart.startsWith("var ")) {
                    get(firstPart.split("=")[0].substring(3).replace(" ", ""));
                }
            }
        }

        List<AssemblyOperation> operations = new ArrayList<>(compilePart(clean, null));

        updateMemoryRegs();

        StringBuilder builder = new StringBuilder();

        builder.append("length = ").append(length).append("\n");

        for (int i = 0; i < headers.size(); i++) {
            int searched = i;
            int val = -1;

            for (int j = 0; j < operations.size(); j++) {
                if (operations.get(j).headers.contains(searched)) {
                    val = j + headerOffsets.get(i);
                    break;
                }
            }

            if (val == -1)
                throw new CompilationException("Header not found :(");

            builder.append("set ").
                    append(headers.get(i).memoryPosition).
                    append(" ").append(val).
                    append("\n");
        }

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

    public List<String> separateCurved(List<String> input){
        List<String> result = new ArrayList<>();

        // Sugar for {} to not need new lines
        for (String line : input) {
            StringBuilder token = new StringBuilder();
            for (char c : line.toCharArray()) {
                if (c == '{' || c == '}') {
                    if (token.length() > 0) {
                        result.add(token.toString());
                        token.setLength(0);
                    }
                    result.add(String.valueOf(c));
                } else {
                    token.append(c);
                }
            }
            if (token.length() > 0) {
                result.add(token.toString());
            }
        }

        // Clean empty lines
        List<String> returnVal = new ArrayList<>();
        for (String line : result) {
            if (!line.trim().isEmpty()) {
                returnVal.add(line.trim());
            }
        }

        return returnVal;
    }

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
        int forbiddenChar = -1; // default: none found
        for (char c : name.toCharArray()) {
            if (FORBIDDEN_SYMBOLS.indexOf(c) >= 0) {
                forbiddenChar = FORBIDDEN_SYMBOLS.indexOf(c);
                break;
            }
        }

        if (forbiddenChar != -1)
            throw new CompilationException(
                    String.format("Variable %s uses forbidden symbol: %s", name, FORBIDDEN_SYMBOLS.charAt(forbiddenChar)));

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

        for (Variable var : headers) {
            var.memoryPosition = currentId++;
        }
        
        for (Variable var : constants) {
            var.memoryPosition = currentId++;
        }

        for (Variable var : variables) {
            var.memoryPosition = currentId++;
        }

        length = currentId;

        System.out.println("\nHeaders:");
        for (int i = 0; i < headers.size(); i++) {
            System.out.printf("%d%n", headers.get(i).memoryPosition);
        }

        System.out.println("\nConstants:");
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("%f : %d%n", constantValues.get(i), constants.get(i).memoryPosition);
        }

        System.out.println("\nVariables:");
        for (int i = 0; i < variables.size(); i++) {
            System.out.printf("%s : %d%n", names.get(i), variables.get(i).memoryPosition);
        }
    }

    public class Variable {
        int memoryPosition;
        final String debugName;
        public int macroTempId = -1;

        public Variable(String debugName) {
            this.debugName = debugName;
        }

        @Override
        public String toString() {
            return "Variable: " + debugName;
        }
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

                        if (arguments.length == 0) {
                            arguments = new Variable[] {getTemporary()};
                        }

                        result.addAll(getAssemblyOfBuiltIn(builtInFunction, arguments));

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
            "printNum",
            "print",
            "ia", // Iterate After
            "end",
            "floor",
            "round",
            "ceil",
            "time",
            "lnew",
            "lget",
            "llen",
            "lamount",
            "ladd",
            "lremove",
            "lempty",
            "lreset",
            "linsert",
            "lreverse",
            "lshuffle",
            "lsort",
            "sleep",
            "random",
            "clear",
            "lset",
            "lprint",
            "lprintNum",
            "lprintSep",
            "lsetTo",
            "setColor",
            "setPosition",
            "setTextSize",
            "ldrawTriangles",
            "ldrawText",
            "dStart",
            "dEnd",
            "isPressed",
            "pi",
            "isMousePressed",
            "mouseX",
            "mouseY",
            "getScroll",
            "width",
            "height"
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
            1,  // "ia" // Iterate After
            1, // end Code
            1,  //"floor",
            1,  //"round",
            1,  //"ceil",
            0,  //"getTime"
            0,  // "newList"
            2,  // "lget"
            1,  // "len"
            0, // amount of lists
            2,  // ladd
            2,  // lremove
            1,   // lempty
            0,  // lreset
            3,   // linsert
            1,   // lreverse
            1,   // lshuffle
            2,   // lsort
            1,   // sleep
            0,   // random
            0,    // clear
            3,   // listSet
            1, //  lprint
            1,  // lprintNum
            3,   // lprintSep
            -1,  // lsetTo
            4,    // "setActiveColor",
            2,    // "setCursorPosition",
            1,    // "setTextSize",
            1,    // "ldrawTriangles",
            1,    // "ldrawText",
            0,    // "beginDrawing",
            0,    // "endDrawing",
            1,    // "isPressed"
            0,     // "pi"
            1,    //"isMousePressed",
            0,    //"mouseX",
            0,    //"mouseY",
            0,    //"getScroll",
            0,    //"width",
            0    //"height"
    };

    public List<AssemblyOperation> getAssemblyOfBuiltIn(int functionId, Variable[] arguments) {
        List<AssemblyOperation> result = new ArrayList<>();

        switch (functionId){
            case 0, 1, 2, 3, 4, 7, 16, 17, 18: // sin, cos, tan, asin, acos, not, floor, round, ceil
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
                result.add(new AssemblyOperation(Type.UPDATE_CONSOLE, new Variable[] {}));
                break;
            case 13:
                // Print the chars and ending with NO new line!
                for (Variable argument : arguments)
                    result.add(new AssemblyOperation(Type.PRINT, new Variable[]{argument}));
                result.add(new AssemblyOperation(Type.UPDATE_CONSOLE, new Variable[] {}));
                break;
            case 14: // Iterate After
                Variable tempIterationAfterReturn = getTemporary();
                Variable iterateAfter = arguments[0];

                result.add(new AssemblyOperation(Type.COPY, new Variable[] {arguments[0], tempIterationAfterReturn}));
                result.add(new AssemblyOperation(Type.ITERATE, new Variable[] {iterateAfter}));

                arguments[0] = tempIterationAfterReturn;
                break;
            case 15: // End
                result.add(new AssemblyOperation(Type.END, new Variable[] {arguments[0]}));
                break;
            case 19: // Time
                result.add(new AssemblyOperation(Type.TIME, new Variable[] {arguments[0]}));
                break;
            case 20: // new list
                result.add(new AssemblyOperation(Type.NEW_LIST, new Variable[] {arguments[0]}));
                break;
            case 21: // get from list
                Variable tempListGet = getTemporary();

                result.add(new AssemblyOperation(Type.COPY_FROM_LIST, new Variable[] {arguments[0], arguments[1], tempListGet}));

                arguments[0] = tempListGet;
                break;
            case 22: // length of list
                result.add(new AssemblyOperation(Type.LENGTH_OF_LIST, new Variable[] {arguments[0], arguments[0]}));
                break;
            case 23: // list amounts
                result.add(new AssemblyOperation(Type.LIST_AMOUNT, new Variable[] {arguments[0]}));
                break;
            case 24: // Add to list
                result.add(new AssemblyOperation(Type.ADD_LIST, new Variable[] {arguments[0], arguments[1]}));
                break;
            case 25: // Remove from list
                result.add(new AssemblyOperation(Type.REMOVE_AT_LIST, new Variable[] {arguments[0], arguments[1]}));
                break;
            case 26: // empty list
                result.add(new AssemblyOperation(Type.EMPTY_LIST, new Variable[] {arguments[0]}));
                break;
            case 27: // remove all
                result.add(new AssemblyOperation(Type.REMOVE_ALL_LISTS, new Variable[] {}));
                break;
            case 28: // Insert
                result.add(new AssemblyOperation(Type.ADD_AT_LIST, new Variable[] {arguments[0], arguments[1], arguments[2]}));
                break;
            case 29: // Reverse
                result.add(new AssemblyOperation(Type.REVERSE_LIST, new Variable[] {arguments[0]}));
                break;
            case 30: // Shuffle
                result.add(new AssemblyOperation(Type.SHUFFLE_LIST, new Variable[] {arguments[0]}));
                break;
            case 31: // Sort
                result.add(new AssemblyOperation(Type.SORT_LIST, new Variable[] {arguments[0], arguments[1]}));
                break;
            case 32: // Sleep
                result.add(new AssemblyOperation(Type.SLEEP, new Variable[] {arguments[0]}));
                break;
            case 33: // Random
                result.add(new AssemblyOperation(Type.RANDOM, new Variable[] {arguments[0]}));
                break;
            case 34: // clear
                result.add(new AssemblyOperation(Type.CLEAR_CONSOLE, new Variable[] {}));
                break;
            case 35: // lset
                result.add(new AssemblyOperation(Type.LIST_SET, new Variable[] {arguments[0], arguments[1], arguments[2]}));
                break;
            case 36: // lprint
                result.add(new AssemblyOperation(Type.PRINT_VECTOR, new Variable[] {arguments[0]}));
                result.add(new AssemblyOperation(Type.UPDATE_CONSOLE, new Variable[] {}));
                break;
            case 37: // lprintNum
                result.add(new AssemblyOperation(Type.PRINT_VECTOR_NUMBERS, new Variable[] {arguments[0]}));
                result.add(new AssemblyOperation(Type.UPDATE_CONSOLE, new Variable[] {}));
                break;
            case 38: // lprintSep
                result.add(new AssemblyOperation(Type.PRINT_VECTOR_SEPARATED, new Variable[] {arguments[0], arguments[1], arguments[2]}));
                result.add(new AssemblyOperation(Type.UPDATE_CONSOLE, new Variable[] {}));
                break;
            case 39: // lsetTo
                result.add(new AssemblyOperation(Type.EMPTY_LIST, new Variable[] {arguments[0]}));
                for (int i = 1; i < arguments.length; i++) {
                    result.add(new AssemblyOperation(Type.ADD_LIST, new Variable[] {arguments[0], arguments[i]}));
                }
                break;
            case 40: // set Active Color
                double[] constants = {256, 65536, 16777216};

                for (int i = 1; i < 4; i++) {
                    result.add(new AssemblyOperation(Type.MULTIPLY, new Variable[] {arguments[i], getConstant(constants[i-1]), arguments[i]}));
                    result.add(new AssemblyOperation(Type.ADD, new Variable[] {arguments[i], arguments[0], arguments[0]}));
                }

                result.add(new AssemblyOperation(Type.SET_COLOR, new Variable[] {arguments[0]}));
                break;
            case 41: // "setCursorPosition",
                result.add(new AssemblyOperation(Type.SET_POSITION, new Variable[] {arguments[0], arguments[1]}));
                break;
            case 42: // "setTextSize",
                result.add(new AssemblyOperation(Type.SET_TEXT_SIZE, new Variable[] {arguments[0]}));
                break;
            case 43:
                result.add(new AssemblyOperation(Type.DRAW_TRIANGLES, new Variable[] {arguments[0]}));
                break;
            case 44:
                result.add(new AssemblyOperation(Type.DRAW_TEXT, new Variable[] {arguments[0]}));
                break;
            case 45:
                result.add(new AssemblyOperation(Type.BEGIN_DRAWING, new Variable[] {}));
                break;
            case 46:
                result.add(new AssemblyOperation(Type.END_DRAWING, new Variable[] {}));
                break;
            case 47:
                Variable tempIsPressed = getTemporary();

                result.add(new AssemblyOperation(Type.IS_PRESSED, new Variable[] {arguments[0], tempIsPressed}));

                arguments[0] = tempIsPressed;
                break;
            case 48: // pi()
                Variable tempPi = getTemporary();

                result.add(new AssemblyOperation(Type.COPY, new Variable[] {getConstant(Math.PI), tempPi}));

                arguments[0] = tempPi;
                break;
            case 49: // is mouse pressed
                result.add(new AssemblyOperation(Type.MOUSE_PRESSED, new Variable[] {arguments[0], arguments[0]}));
                break;
            case 50: // mouse x
                result.add(new AssemblyOperation(Type.GET_MOUSE_X, new Variable[] {arguments[0]}));
                break;
            case 51: // mouse y
                result.add(new AssemblyOperation(Type.GET_MOUSE_Y, new Variable[] {arguments[0]}));
                break;
            case 52: // get Scroll
                result.add(new AssemblyOperation(Type.GET_SCROLL, new Variable[] {arguments[0]}));
                break;
            case 53: // Width
                result.add(new AssemblyOperation(Type.GET_WIDTH, new Variable[] {arguments[0]}));
                break;
            case 54: // Height
                result.add(new AssemblyOperation(Type.GET_HEIGHT, new Variable[] {arguments[0]}));
                break;
            default:
                throw new CompilationException("Unknown built-in function with id: " + functionId);
        }

        return result;
    }

    class AssemblyOperation {
        Type type;
        Variable[] vars;

        public List<Integer> headers = new ArrayList<>();

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
        JUMP,
        END,
        FLOOR,
        ROUND,
        CEIL,
        TIME,
        NEW_LIST,
        COPY_FROM_LIST,
        LENGTH_OF_LIST,
        LIST_AMOUNT,
        ADD_LIST,
        REMOVE_AT_LIST,
        EMPTY_LIST,
        REMOVE_ALL_LISTS,
        ADD_AT_LIST,
        REVERSE_LIST,
        SHUFFLE_LIST,
        SORT_LIST,
        SLEEP,
        RANDOM,
        CLEAR_CONSOLE,
        LIST_SET,
        UPDATE_CONSOLE,
        PRINT_VECTOR,
        PRINT_VECTOR_NUMBERS,
        PRINT_VECTOR_SEPARATED,
        SET_COLOR,
        SET_POSITION,
        SET_TEXT_SIZE,
        DRAW_TRIANGLES,
        DRAW_TEXT,
        BEGIN_DRAWING,
        END_DRAWING,
        IS_PRESSED,
        MOUSE_PRESSED,
        GET_MOUSE_X,
        GET_MOUSE_Y,
        GET_SCROLL,
        GET_WIDTH,
        GET_HEIGHT
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
            case END -> {
                return "end";
            }
            case FLOOR -> {
                return "floor";
            }
            case ROUND -> {
                return "round";
            }
            case CEIL -> {
                return "ceil";
            }
            case TIME -> {
                return "time";
            }
            case NEW_LIST -> {
                return "newList";
            }
            case COPY_FROM_LIST -> {
                return "copyFromList";
            }
            case LENGTH_OF_LIST -> {
                return "lengthOfList";
            }
            case LIST_AMOUNT -> {
                return "listAmount";
            }
            case ADD_LIST -> {
                return "addList";
            }
            case REMOVE_AT_LIST -> {
                return "removeAtList";
            }
            case EMPTY_LIST -> {
                return "emptyList";
            }
            case REMOVE_ALL_LISTS -> {
                return "removeAll";
            }
            case ADD_AT_LIST -> {
                return "addAtList";
            }
            case REVERSE_LIST -> {
                return "reverseList";
            }
            case SHUFFLE_LIST -> {
                return "shuffleList";
            }
            case SORT_LIST -> {
                return "sortList";
            }
            case SLEEP -> {
                return "sleep";
            }
            case RANDOM -> {
                return "random";
            }
            case CLEAR_CONSOLE -> {
                return "clearConsole";
            }
            case LIST_SET -> {
                return "listSet";
            }
            case UPDATE_CONSOLE -> {
                return "updateConsole";
            }
            case PRINT_VECTOR -> {
                return "printVector";
            }
            case PRINT_VECTOR_NUMBERS -> {
                return "printVectorNumbers";
            }
            case PRINT_VECTOR_SEPARATED -> {
                return "printVectorSeparated";
            }
            case SET_COLOR -> {
                return "setColor";
            }
            case SET_POSITION -> {
                return "setPosition";
            }
            case SET_TEXT_SIZE -> {
                return "setTextSize";
            }
            case DRAW_TRIANGLES -> {
                return "drawTriangles";
            }
            case DRAW_TEXT -> {
                return "drawText";
            }
            case BEGIN_DRAWING -> {
                return "beginDrawing";
            }
            case END_DRAWING -> {
                return "endDrawing";
            }
            case IS_PRESSED -> {
                return "isPressed";
            }
            case MOUSE_PRESSED -> {
                return "mousePressed";
            }
            case GET_MOUSE_X -> {
                return "getMouseX";
            }
            case GET_MOUSE_Y -> {
                return "getMouseY";
            }
            case GET_SCROLL -> {
                return "getScroll";
            }
            case GET_WIDTH -> {
                return "getWidth";
            }
            case GET_HEIGHT -> {
                return "getHeight";
            }
        }
        throw new CompilationException("Unknown operation type :(, " + type);
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
            case 16 -> Type.FLOOR;
            case 17 -> Type.ROUND;
            case 18 -> Type.CEIL;

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
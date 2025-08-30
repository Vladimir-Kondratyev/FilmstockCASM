import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class ArithmeticCompiler {
    public static final String[][] OPERATION_CHARS = {
            new String[] {"^"},
            new String[] {"*", "/", "%"},
            new String[] {"+", "-"},
            new String[] {"&", "|", "?", ">", "<"}
    };

    public static final String[] OPERATIONS = new String[] {"&", "|", "?", ">", "<", "^", "*", "/", "+", "-", "%"};

    public static final String NUMERICAL_REGEX = "[0-9.]";

    public static final String OPERATIONS_REGEX = "[&|?><^*/+\\-%]";

    public static final String OPERATIONS_AND_COMMA_REGEX = "[&|?><^*/+\\-%,]";

    public static final String UNARY_REGEX = "[&|?><^*/+\\-%(,]";

    public static final String PARENTHESIS_REGEX = "[()]";

    public static boolean isParenthesOrComma(String input) {
        String removed = input.replaceAll(OPERATIONS_AND_COMMA_REGEX, "");
        return removed.isEmpty();
    }

    public static boolean isParenthesis(String input) {
        String removed = input.replaceAll(PARENTHESIS_REGEX, "");
        return removed.isEmpty();
    }

    public static boolean isNumerical(String input) {
        String removed = input.replaceAll(NUMERICAL_REGEX, "");
        return removed.isEmpty();
    }

    public static boolean isOperation(String input) {
        String removed = input.replaceAll(OPERATIONS_REGEX, "");
        return removed.isEmpty();
    }

    public static boolean isUnary(String input) {
        String removed = input.replaceAll(UNARY_REGEX, "");
        return removed.isEmpty();
    }

    public static int getPriority(String o) {
        for (int i = 0; i < OPERATION_CHARS.length; i++) {
            for (String opc : OPERATION_CHARS[i])
                if (Objects.equals(o, opc))
                    return i;
        }
        throw new CompilationException("Unknown operation: " + o);
    }

    public static void main(String[] args) {
        Compiler compiler = new Compiler();

        compiler.get("a");
        compiler.get("b");
        compiler.get("c");
        compiler.get("d");

        Compiler.Node node = stringToNode(compiler, "f(2,3,4,5)", true);

        System.out.println(node);
    }

    public static Compiler.Node compile(Compiler compiler, String input) {
        return stringToNode(compiler, input, true);
    }

    private static Compiler.Node stringToNode(Compiler compiler, String input, boolean isHead) {
        String stripped = input.replace(" ", "");

        stripped = stripped.replace("--", "+");

        //#region Deal with unary minus
        if (isHead) {
            int unaryMinusAmount = 0;

            for (int i = 0; i < stripped.length(); i++) {
                // In the beginning or if 2 operations in a row or if a minus after ( or ,
                if (stripped.charAt(i) == '-') {
                    if (i == 0)
                        unaryMinusAmount++;
                    else if (isUnary(String.valueOf(stripped.charAt(i-1))))
                        unaryMinusAmount++;
                }
            }

            // Replace things like -2 with (0 - 2) and things like -(...) with (0-(...))
            for (int m = 0; m < unaryMinusAmount; m++) {
                for (int start = 0; start < stripped.length(); start++) {
                    // In the beginning or if 2 operations in a row or if a minus after ( or ,
                    if (stripped.charAt(start) == '-') {
                        boolean doBreak = false;
                        boolean searchEnd = false;

                        if (start == 0)
                            searchEnd = true;
                        else if (isUnary(String.valueOf(stripped.charAt(start-1))))
                            searchEnd = true;

                        if (searchEnd) {
                            char charAfter = stripped.charAt(start+1);
                            boolean isParenthesis = charAfter == '(';

                            doBreak = true;

                            int end = 0;

                            // If it's not a parenthesis, it can also be a function call, hence the isParenthesis check
                            if (!isParenthesis) {
                                for (end = start + 1; end < stripped.length(); end++) {
                                    String s = String.valueOf(stripped.charAt(end));
                                    if (isParenthesOrComma(s)){
                                        break;
                                    }
                                    if (isParenthesis(s)) {
                                        isParenthesis = true;
                                        break;
                                    }
                                }
                            }

                            if (isParenthesis) {
                                int depth = 1;
                                for (end = start + 1; end < stripped.length(); end++) {
                                    char c = stripped.charAt(end);
                                    if (c == '(')
                                        depth++;
                                    else if (c == ')')
                                        depth--;

                                    if (depth == 0) {
                                        end++;
                                        break;
                                    }
                                }
                            }

                            stripped = stripped.substring(0, start) + "(0" + stripped.substring(start, end)
                                    + ")"
                                    + stripped.substring(end);
                        }

                        if (doBreak)
                            break;
                    }
                }
            }
        }
        //#endregion

        List<Compiler.Node> parenthesisNodes = new ArrayList<>();
        //#region Deal with parenthesis
        int openAmount = 0;
        int closedAmount = 0;

        for (int i = 0; i < stripped.length(); i++) {
            switch (stripped.charAt(i)){
                case '(':
                    openAmount++;
                    break;
                case ')':
                    closedAmount++;
                    break;
            }
        }

        if (openAmount != closedAmount)
            throw new CompilationException("Parenthesis mismatch: " + input);

        if (openAmount != 0) {
            List<Integer> parenthesisStart = new ArrayList<>();
            List<Integer> parenthesisEnd = new ArrayList<>();
            List<Integer> functionLengths = new ArrayList<>();

            int depth = 0;

            for (int i = 0; i < stripped.length(); i++) {
                switch (stripped.charAt(i)){
                    case '(':
                        depth++;
                        if (depth == 1){
                            parenthesisStart.add(i);
                        }
                        break;
                    case ')':
                        depth--;
                        if (depth == 0){
                            parenthesisEnd.add(i);
                        }
                        break;
                }
            }

            String fixedParenthesis = new String(stripped);

            for (int i = 0; i < parenthesisStart.size(); i++) {
                String f = null;
                if (parenthesisStart.get(i) != 0) {
                    if (!isOperation(String.valueOf(fixedParenthesis.charAt(parenthesisStart.get(i)-1)))) {
                        int funcStart;
                        for (funcStart = parenthesisStart.get(i)-1; funcStart > -1; funcStart--) {
                            if (isOperation(String.valueOf(fixedParenthesis.charAt(funcStart))) || funcStart == 0){
                                break;
                            }
                        }

                        if (funcStart != 0)
                            funcStart++;

                        f = fixedParenthesis.substring(funcStart, parenthesisStart.get(i));
                    }
                }

                // Create a subNode for the function call
                if (f != null) {
                    functionLengths.add(f.length());

                    String sub = fixedParenthesis.substring(parenthesisStart.get(i)+1, parenthesisEnd.get(i));

                    if (!sub.isEmpty()) {
                        String[] functionArguments = sub.split(",");

                        Compiler.Node[] argumentNodes = new Compiler.Node[functionArguments.length];

                        for (int j = 0; j < functionArguments.length; j++) {
                            argumentNodes[j] = stringToNode(compiler, functionArguments[j], false);
                        }

                        Compiler.Node functionCallNode = compiler.nodeFromData(List.of(argumentNodes), f);

                        parenthesisNodes.add(functionCallNode);
                    }
                    else {
                        Compiler.Node functionCallNode = compiler.nodeFromData(new ArrayList<>(), f);

                        parenthesisNodes.add(functionCallNode);
                    }
                }

                else {
                    functionLengths.add(0);

                    parenthesisNodes.add(stringToNode(compiler,
                            fixedParenthesis.
                                    substring(parenthesisStart.get(i)+1, parenthesisEnd.get(i)), false));
                }
            }

            for (int i = parenthesisStart.size() - 1; i > -1; i--) {
                fixedParenthesis = fixedParenthesis.substring(0, parenthesisStart.get(i)-functionLengths.get(i))
                        + "@" + fixedParenthesis.substring(parenthesisEnd.get(i) + 1);
            }

            stripped = fixedParenthesis;
        }

        //#endregion

        List<String> operationTokens = new ArrayList<>();
        List<String> tokensStrings = new ArrayList<>();

        tokensStrings.add(stripped);

        for (String op : OPERATIONS) {
            List<String> toAdd = new ArrayList<>();
            for (String ts : tokensStrings) {
                String[] tsSeparated = ts.split(Pattern.quote(op));
                for (String s : tsSeparated)
                    toAdd.add(s);
            }
            tokensStrings = toAdd;
        }

        int currentChar = 0;

        for (String tokenString : tokensStrings) {
            currentChar += tokenString.length();
            if (currentChar < stripped.length()) {
                operationTokens.add(stripped.substring(currentChar, currentChar + 1));
                currentChar++;
            }
            else break;
        }

        int latestNode = 0;
        List<Object> tokens = new ArrayList<>();

        for (String tokenString : tokensStrings) {
            Object token;
            if (compiler.doesVarExist(tokenString))
                token = compiler.get(tokenString);

            else if (isNumerical(tokenString)){
                double value = Double.parseDouble(tokenString);
                token = compiler.getConstant(value);
            }

            else if (tokenString.equals("@")){
                token = parenthesisNodes.get(latestNode);
                latestNode++;
            }

            else
                throw new CompilationException(String.format("Variable %s does not exist: %s", tokenString, input));

            tokens.add(token);
        }

        int length = operationTokens.size();

        for (int i = 0; i < length; i++) {
            for (int j = 0; j < OPERATION_CHARS.length; j++) {
                boolean doBreak = false;
                for (int o = 0; o < operationTokens.size(); o++){
                    if (getPriority(operationTokens.get(o)) == j) {
                        Compiler.Node[] children = new Compiler.Node[2];
                        for (int k = 1; k >= 0; k--)  {
                            Object token = tokens.get(o + k);
                            if (token instanceof Compiler.Variable varToken) {
                                children[k] = compiler.nodeFromData(varToken);
                            } else if (token instanceof Compiler.Node nodeToken) {
                                children[k] = nodeToken;
                            } else {
                                throw new IllegalStateException("Unexpected token type: " + token.getClass());
                            }
                        }

                        Compiler.Node parent = compiler.nodeFromData(operationTokens.get(o), List.of(children));

                        tokens.set(o, parent);
                        tokens.remove(o+1);

                        operationTokens.remove(o);
                        doBreak = true;
                        break;
                    }
                }
                if (doBreak)
                    break;
            }
        }

        if (tokens.get(0) instanceof Compiler.Variable) {
            tokens.set(0, compiler.nodeFromData((Compiler.Variable) tokens.get(0)));
        }

        return (Compiler.Node) tokens.get(0);
    }
}

import java.util.ArrayList;
import java.util.Arrays;
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
    public static final String NUMERICAL = "[0-9.]";

    public static boolean isNumerical(String input) {
        String removed = input.replaceAll(NUMERICAL, "");
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
        compiler.get("femboys");

        Compiler.Node node = stringToNode(compiler, "a=femboys+1*2/(3&4)+1-3", true);

        System.out.println(node);
    }

    public static Compiler.Node stringToNode(Compiler compiler, String input, boolean isHead) {
        String stripped = input.replace(" ", "");

        String[] separated;
        if (isHead) {
             separated = stripped.split("=");

            if (separated.length != 2)
                throw new CompilationException("Incorrect variable manipulation: " + input);

            if (!compiler.doesVarExist(separated[0]))
                throw new CompilationException(String.format("Variable %s does not exist: %s", separated[0], input));
        }
        else {
            separated = new String[] {"", stripped};
        }

        List<Compiler.Node> parenthesisNodes = new ArrayList<>();
        //#region deal with parenthesis
        int openAmount = 0;
        int closedAmount = 0;

        for (int i = 0; i < separated[1].length(); i++) {
            switch (separated[1].charAt(i)){
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

            int depth = 0;

            for (int i = 0; i < separated[1].length(); i++) {
                switch (separated[1].charAt(i)){
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

            String fixedParenthesis = new String(separated[1]);

            for (int i = 0; i < parenthesisStart.size(); i++) {
                parenthesisNodes.add(stringToNode(compiler,
                        fixedParenthesis.
                                substring(parenthesisStart.get(i)+1, parenthesisEnd.get(i)), false));
            }

            for (int i = parenthesisStart.size() - 1; i > -1; i--) {
                fixedParenthesis = fixedParenthesis.substring(0, parenthesisStart.get(i))
                        + "@" + fixedParenthesis.substring(parenthesisEnd.get(i) + 1);
            }

            separated[1] = fixedParenthesis;
        }

        //#endregion

        List<String> operationTokens = new ArrayList<>();
        List<String> tokensStrings = new ArrayList<>();

        tokensStrings.add(separated[1]);

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
            if (currentChar < separated[1].length()) {
                operationTokens.add(separated[1].substring(currentChar, currentChar + 1));
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

        return (Compiler.Node) tokens.get(0);
    }
}

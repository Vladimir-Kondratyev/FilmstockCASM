public class CompilationException extends RuntimeException {
    public CompilationException(String message) {
        super("Couldn't compile :(\n" + message);
    }

    public CompilationException(String message, Compiler.Line line) {
        super(String.format("Couldn't compile :(\n" + "Line: %s%nCompiled code: %s%nCompilation Error: %s", line.id, line.code, message));
    }
}

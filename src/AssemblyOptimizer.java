import java.util.ArrayList;
import java.util.List;

public abstract class AssemblyOptimizer {
    public static List<Compiler.AssemblyOperation> optimizeAll(List<Compiler.AssemblyOperation> operations) {
        operations = optimizeCopiesForward(operations);
        return optimizeCopiesBackward(operations);
    }

    public static List<Compiler.AssemblyOperation> optimizeCopiesForward(List<Compiler.AssemblyOperation> operations) {
        List<Compiler.AssemblyOperation> optimized = new ArrayList<>();

        // Optimize temporary variable usage
        for (int i = 0; i < operations.size(); i++) {
            Compiler.AssemblyOperation current = operations.get(i);

            // If the current operation is the last, there cannot be a copy after this
            if (i == operations.size() - 1) {
                optimized.add(current);
                break;
            }

            Compiler.AssemblyOperation next = operations.get(i + 1);

            int currentOut = Compiler.getOutputArg(current.type);

            if (currentOut == -1 || next.type != Compiler.Type.COPY) {
                optimized.add(current);
                continue;
            }

            if (!current.vars[currentOut].isTemporary) {
                optimized.add(current);
                continue;
            }

            Compiler.Variable outVar = current.vars[currentOut];

            if (next.vars[0] == outVar) {
                current.vars[currentOut] = next.vars[1];
                optimized.add(current);

                current.headers.addAll(next.headers);

                i++;
                continue;
            }

            optimized.add(current);
        }

        return optimized;
    }

    public static List<Compiler.AssemblyOperation> optimizeCopiesBackward(List<Compiler.AssemblyOperation> operations) {
        List<Compiler.AssemblyOperation> optimized = new ArrayList<>();

        // Optimize temporary variable usage
        for (int i = 0; i < operations.size(); i++) {
            Compiler.AssemblyOperation current = operations.get(i);

            // If the current operation is the last, there cannot be a copy after this
            if (i == operations.size() - 1) {
                optimized.add(current);
                break;
            }

            Compiler.AssemblyOperation next = operations.get(i + 1);

            if (current.type != Compiler.Type.COPY) {
                optimized.add(current);
                continue;
            }

            Compiler.Variable outVar = current.vars[1];
            Compiler.Variable inVar = current.vars[0];

            if (!outVar.isTemporary) {
                optimized.add(current);
                continue;
            }

            // Check if it is used in the next instruction
            List<Integer> whereUsed = new ArrayList<>();
            for (int j = 0; j < next.vars.length; j++) {
                if (j == Compiler.getOutputArg(next.type)) continue;

                if (next.vars[j] == outVar) {
                    whereUsed.add(j);
                }
            }

            // If it's unused, no point in optimizing
            if (whereUsed.isEmpty()) {
                optimized.add(current);
                continue;
            }

            for (Integer integer : whereUsed) {
                next.vars[integer] = inVar;
            }

            next.headers.addAll(current.headers);

            optimized.add(next);
            i++;
        }

        return optimized;
    }

}

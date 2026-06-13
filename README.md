# FilmstockCASM

Compiler and assembler for the **Filmstock** toolchain. Takes Filmstock source code (`.fss`) and produces `.filmstock` binaries that run on [FilmstockVM](https://github.com/Vovencio/FilmstockVM).

**Toolchain repositories:**
- [FilmstockVM](https://github.com/Vovencio/FilmstockVM) - Virtual machine that executes `.filmstock` binaries
- [FilmstockBin](https://github.com/Vovencio/FilmstockBin) - Filmstock toolchain.

## Technicalities

### Pipeline

```
  source.fss
      │
      V  Compiler  (Compiler.java)
  intermediate.aroll     <- Filmstock Assembly, semi human-readable text
      │
      V  Assembly Optimizations  (AssemblyOptimizer.java)
  optimized.aroll        <- Filmstock Assembly, semi human-readable text
      │
      V  Assembler  (Assembler.java)
  output.filmstock       <- binary, ready for the VM
```

The compilation and assembly stages can be run separately or combined in one step with `-build`.

### Filmstock Assembly (`.aroll`)

The intermediate text format the compiler emits and the assembler consumes. It can be written or inspected by hand.

```
length 32           // total film[] size the VM allocates

set 5 3.14159       // pre-initialise film[5] = 3.14159
set 6 0

// instructions: opcode arg1 arg2 arg3
add 5 6 5           // film[5] = film[5] + film[6]
end 6               // halt with exit code film[6]
```

- First line must be `length N`.
- All `set INDEX VALUE` lines must appear contiguously immediately after the length line.
- `//` comments are stripped by the assembler.
- Full opcode reference: [FilmstockVM](https://github.com/Vovencio/FilmstockVM).

### Memory layout

The compiler assigns contiguous `film[]` indices in this order:

1. **Temporaries** - things you would have on the stack in other languages.
2. **Jump target pointers** (headers) - the jump destinations are stored in these separate header variables.
3. **Constants** - deduplicated numeric literals (including `pi()`, etc.).
4. **Named variables** - all user-declared `var` names, in declaration order.

### Data model

- The only datatype is `double`. "Booleans" are `0` (false) and any non-zero value (true).
- Characters are doubles in `[0, 255]`; `print` emits `floor(value % 256)` as ASCII.
- List pointers are doubles whose integer part is the internal list ID.

### File conventions

| Extension | Contents |
|---|---|
| `.fss` | Filmstock scripts |
| `.aroll` | Filmstock Assembly (intermediate text) |
| `.filmstock` | Compiled bytecode for the VM |

### Limitations

- Recursive macros malfunction due to static memory allocation.
- All built-in functions return exactly one value.

### Planned features

- Light JIT

---

## Building & running

The toolchain is a plain Java project requiring **JDK 17** or newer.

```sh
javac src/*.java -d out/
java -cp out Main -build program.fss program.filmstock
```

Or with the pre-built JAR:

```sh
java -jar bin/Filmstock.jar -build program.fss program.filmstock
```

### CLI reference

Multiple commands can be chained in one invocation.

```
-build    <in.fss>   <out.filmstock>   Compile + assemble in one step
-compile  <in.fss>   <out.aroll>       Compile only (source → assembly text)
-assemble <in.aroll> <out.filmstock>   Assemble only (assembly text → binary)
-debug                                 Print verbose info for the next step
-test                                  Compile test.script → test.aroll → test.roll
-O         <0-%d / help>      : Sets the optimization level or prints information about optimizations.\n
-help                                  Print this list
```

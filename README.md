# FilmstockCASM

Compiler and assembler for the **Filmstock** toolchain. Takes Filmstock source code (`.fss`) and produces `.filmstock` binaries that run on [FilmstockVM](https://github.com/Vovencio/FilmstockVM).

**Toolchain repositories:**
- [FilmstockVM](https://github.com/Vovencio/FilmstockVM) - Virtual machine that executes `.filmstock` binaries
- [FilmstockBin](https://github.com/Vovencio/FilmstockBin) - Filmstock toolchain.

---

## What is Filmstock

Film Stock is a simple toy programming language that revolves around the VM having a predefined stack size and having only one datatype: **doubles**. Source code gets compiled into Filmstock Assembly, which is then assembled into bytecode. Filmstock currently supports Windows and Linux and is cross platform.

The language uses standard scoping with variable shadowing.

When performing operations with variables, variable values are **copied**. That means calling `sin(x)` by itself does not mutate `x`. The call operates on a copy. List data is handled differently: list operations do **not** copy the list itself and operate on list pointers via list functions.

> **Note:** Film Stock currently has no user-defined functions - only *macros*. Built-in functions all return a single value; see [Technicalities](#technicalities) for details.

You can find 4 simple examples of Filmstock and an interactive 2D rigidbody simulation ("balls") in the `examples` directory in [FilmstockBin](https://github.com/Vovencio/FilmstockBin).

---

## Language

### Statements

```
#include PATH                    Add all lines from PATH at this position

var VARIABLE_NAME = EXPRESSION   Initialise a variable with the value of EXPRESSION
VARIABLE_NAME = EXPRESSION       Set an existing variable to the value of EXPRESSION

//COMMENT                        Single-line comment

'a'                              Character literal - evaluates to the ASCII code of 'a'
                                 Use \' to escape a single-quote inside
"Fake String"                    String literal - expanded into a sequence of ASCII values
                                 Use \" to escape a double-quote inside
\n  \\  \=  \.                   Escape sequences: newline, backslash, equals, dot

if CONDITION {                   Executes body when CONDITION is non-zero. Creates a new scope.
    ...
} else {
    ...
}

for INIT; COND; UPDATE {         Classic for loop. INIT runs once; body runs while COND is
    ...                          non-zero; UPDATE runs after each iteration. New scope.
}

break                            Immediately exits the nearest enclosing loop

{                                Anonymous block - creates a new scope.
    ...
}
```

Statements are separated by newlines. Any number of blank lines is fine (equivalent to `;` in Java). Variable names may not contain: `$ # @ = & | ? > < ^ * / + \ - % ( ) { } ; , [ ] .` or a newline.

### Arithmetic expressions

```
FUNCTION_NAME(ARGUMENTS)    Call a macro or built-in function
(EXPRESSION)                Grouping

V1 ^ V2                     Power
V1 * V2                     Multiplication
V1 / V2                     Division
V1 % V2                     Non-negative safe modulo
V1 + V2                     Sum
V1 - V2                     Difference

V1 & V2                     Logical AND  - 1 if both non-zero, else 0
V1 | V2                     Logical OR   - 1 if either non-zero, else 0
V1 ? V2                     Equality     - 1 if V1 == V2, else 0
V1 > V2                     Greater than - 1 if V1 > V2, else 0
V1 < V2                     Less than    - 1 if V1 < V2, else 0
```

Compound assignment `x op= expr` expands to `x = x op (expr)` for all binary operators.

### Built-in functions

```
pi()                           Returns π

sin(r)                         Sine (radians)
cos(r)                         Cosine (radians)
tan(r)                         Tangent (radians)
asin(x)                        Arcsine (input in [-1, 1])
acos(x)                        Arccosine (input in [-1, 1])
atan2(y, x)                    Two-argument arctangent

root(a, n)                     nth root of a
sqrt(a)                        Square root (shorthand for root(a, 0.5))

floor(a)                       Floor
round(a)                       Round
ceil(a)                        Ceil

not(a)                         0 if a is non-zero, else 1

time()                         System time in milliseconds since epoch
sleep(a)                       Sleep for a milliseconds
random()                       Non-deterministic random float in [0, 1)

print(a, b, c, ...)            Interpret each argument as an ASCII index and emit the character
                               floor(a % 256) - does not end with a newline
printNum(a, b, c, ...)         Print arguments in decimal with 2 decimal places, separated by ", "
                               - does not end with a newline
nl()                           Print a newline

it(a)                          "Iterate": increment a by 1, return the new value (pre-increment)
ia(a)                          "Iterate After": return current value of a, then increment (post-increment)
```

> `it` and `ia` are the only built-ins that modify the variable in place rather than working on a copy.

### List functions

Lists are dynamic arrays of `double`. All list operations work through a **pointer** (an integer ID stored in a `double`). The recommended syntax uses the dot and bracket `list[index]` sugar. The compiler desugars it automatically. As such, you can use `list.function(...)`, where `list` is a list pointer and will be passed as the first argument to `function`.

```
lamount()                       Number of lists currently allocated
lreset()                        Destroy all lists and free all pointers
lnew()                          Create a new list, return its pointer

lget(ptr, id)                   Return value at index id (0-based)
                                → listName[id]
lset(ptr, id, val)              Set value at index id
                                → listName[id] = val
lsetTo(ptr, values...)          Clear list and fill with given values
                                → listName.setTo(v1, v2, ...)
llen(ptr)                       Return list length
                                → listName.len()

ladd(ptr, value)                Append value
                                → listName.add(value)
lremove(ptr, index)             Remove element at index
                                → listName.remove(index)
lempty(ptr)                     Clear all elements
                                → listName.empty()
linsert(ptr, value, index)      Insert value at index
                                → listName.insert(value, index)

lreverse(ptr)                   Reverse in place    → listName.reverse()
lshuffle(ptr)                   Shuffle in place    → listName.shuffle()
lsort(ptr, type)                Sort: 0 = ascending, else descending
                                → listName.sort(type)

lprint(ptr)                     Print each element as an ASCII character - no trailing newline
                                → listName.print()
lprintNum(ptr)                  Print as [x.xx, y.yy, …] - no trailing newline
                                → listName.printNum()
lprintSep(ptr, sep, f)          Print as characters, inserting sep every f elements - no trailing newline
                                → listName.printSep(sep, f)
```

### Graphics (Raylib)

Calling any of these causes [FilmstockVM](https://github.com/Vovencio/FilmstockVM) to open a window automatically.

```
dStart()                        Begin a draw frame
dEnd()                          End the draw frame and push to screen

setColor(r, g, b, a)            Set draw colour (0–255 per channel)
setPosition(x, y)               Set draw offset from top-left in pixels
setTextSize(size)               Set text size in pixels

ldrawTriangles(ptr)             Draw list as filled triangles
                                → listName.drawTriangles()
                                → Every 6 values: x1, y1, x2, y2, x3, y3
ldrawText(ptr)                  Draw list as text (same encoding as print())
                                → listName.drawText()

isPressed(id)                   1 if key is held, else 0
                                → key ID reference: https://docs.rs/rust-raylib/latest/rust_raylib/ffi/enum.KeyboardKey.html
isMousePressed(button)          1 if mouse button is held, else 0
                                → 0 = left, 1 = right, 2 = middle
mouseX() / mouseY()             Mouse position in window coordinates
getScroll()                     Mouse wheel delta (frame-dependent)
width() / height()              Window dimensions in pixels
```

### Macros

Macros are textually substituted at their call site. No call overhead, no stack frame.

```
#macro NAME(ARG1, ARG2) {
    // Use $ARG1, $ARG2 to reference arguments (pure textual substitution)
    // Use __ as a prefix for locals to avoid name collisions -
    // it expands to the macro name in ALLCAPS + "MACRO" at each call site
    var __temp = $ARG1 + $ARG2
}

#NAME(x, y)                     Expand the macro here - creates a new scope
```

A `__` prefix in a local variable name inside a macro is replaced with `NAMEMACRO` (e.g. inside `#foo`, `__x` becomes `FOOMACRO_x`), preventing collisions when the same macro is inlined multiple times. To print two underscores literally, use `print("_", '_')`.

---

## Technicalities

### Pipeline

```
  source.fss
      │
      ▼  Compiler  (Compiler.java)
  intermediate.aroll     ← Filmstock Assembly, human-readable text
      │
      ▼  Assembler  (Assembler.java)
  output.filmstock       ← binary, ready for FilmstockVM
```

Both stages can be run separately or combined in one step with `-build`.

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
- Full opcode reference: [FilmstockBin](https://github.com/Vovencio/FilmstockBin).

### Memory layout

The compiler assigns contiguous `film[]` indices in this order:

1. **Temporaries** - pooled scratch cells, reused across expressions within each statement.
2. **Jump target pointers** (headers) - one cell per branch or loop target; written as `set` lines.
3. **Constants** - deduplicated numeric literals (including `pi()`, `1` used internally by `root`, etc.).
4. **Named variables** - all user-declared `var` names, in declaration order.

### Data model

- The only datatype is `double`. "Booleans" are `0` (false) and any non-zero value (true).
- Characters are doubles in `[0, 255]`; `print` emits `floor(value % 256)` as ASCII.
- List pointers are doubles whose integer part is the internal list ID.

### File conventions

| Extension | Contents |
|---|---|
| `.fss` or `.script` | Filmstock source code |
| `.aroll` | Filmstock Assembly (intermediate text) |
| `.filmstock` | Compiled bytecode for the VM |

### Limitations

- Recursive macros malfunction due to static memory allocation.
- All built-in functions return exactly one value.

### Planned features

- More list operations in bytecode
- Formatted printing: `printf()`
- True functions
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
-help                                  Print this list
```

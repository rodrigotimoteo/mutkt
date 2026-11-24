# Operators

| Id | Bytecode change | Kotlin before / after | Known equivalent mutants |
|---|---|---|---|
| `arithmetic` | I/L/F/D `ADD`<->`SUB`, `MUL`<->`DIV`, `REM`->`MUL`. Unary `NEG`, `IINC` and string concatenation are not sites. | `a + b` / `a - b`; `a * b` / `a / b`; `a % b` / `a * b` | `x + 0`, `x - 0`, `x * 1`, `x / 1`; `x * 0` vs `x / 0` is not equivalent for int (throws). |

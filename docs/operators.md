# Operators

| Id | Bytecode change | Kotlin before / after | Known equivalent mutants |
|---|---|---|---|
| `arithmetic` | I/L/F/D `ADD`<->`SUB`, `MUL`<->`DIV`, `REM`->`MUL`. Unary `NEG`, `IINC` and string concatenation are not sites. | `a + b` / `a - b`; `a * b` / `a / b`; `a % b` / `a * b` | `x + 0`, `x - 0`, `x * 1`, `x / 1`; `x * 0` vs `x / 0` is not equivalent for int (throws). |
| `conditionals-boundary` | `IFGE`<->`IFGT`, `IFLE`<->`IFLT`, `IF_ICMPGE`<->`IF_ICMPGT`, `IF_ICMPLE`<->`IF_ICMPLT`. Long and double compare via `LCMP`/`DCMPx` then `IFxx`, so they are covered by the `IF*` rows. `IF_ACMP*` is not mutated. | `a < b` / `a <= b`; `x >= 0` / `x > 0` | Loop or range bounds where the equal case is unreachable (`i < n` with `i != n` guaranteed); `> Int.MAX_VALUE`-style guards |

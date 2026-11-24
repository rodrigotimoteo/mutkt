# Operators

| Id | Bytecode change | Kotlin before / after | Known equivalent mutants |
|---|---|---|---|
| `negate-conditionals` | `IFEQ`/`IFNE`, `IFLT`/`IFGE`, `IFGT`/`IFLE`, `IF_ICMPEQ`/`IF_ICMPNE`, `IF_ICMPLT`/`IF_ICMPGE`, `IF_ICMPGT`/`IF_ICMPLE`, `IF_ACMPEQ`/`IF_ACMPNE` swap to their negation | `if (a < b)` compiles to `IF_ICMPGE`, mutated to `IF_ICMPLT` (condition inverted) | Rare; a condition guarding two identical branches. `IFNULL`/`IFNONNULL` are never emitted: Kotlin null checks belong to NULL_SAFETY (P3). |
| `arithmetic` | I/L/F/D `ADD`<->`SUB`, `MUL`<->`DIV`, `REM`->`MUL`. Unary `NEG`, `IINC` and string concatenation are not sites. | `a + b` / `a - b`; `a * b` / `a / b`; `a % b` / `a * b` | `x + 0`, `x - 0`, `x * 1`, `x / 1`; `x * 0` vs `x / 0` is not equivalent for int (throws). |
| `conditionals-boundary` | `IFGE`<->`IFGT`, `IFLE`<->`IFLT`, `IF_ICMPGE`<->`IF_ICMPGT`, `IF_ICMPLE`<->`IF_ICMPLT`. Long and double compare via `LCMP`/`DCMPx` then `IFxx`, so they are covered by the `IF*` rows. `IF_ACMP*` is not mutated. | `a < b` / `a <= b`; `x >= 0` / `x > 0` | Loop or range bounds where the equal case is unreachable (`i < n` with `i != n` guaranteed); `> Int.MAX_VALUE`-style guards |

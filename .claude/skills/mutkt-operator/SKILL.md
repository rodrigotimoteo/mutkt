---
name: mutkt-operator
description: Use when adding or fixing a bytecode mutation operator in mutkt-asm (P1 MVP operators, P3 Kotlin and quick-win operators). Step-by-step recipe and checklist.
---

# Adding a mutation operator

Prerequisite: the operator framework (ROD-88) and test fixtures (ROD-78) exist. If not, stop.

## Steps
1. **Spec.** Read the operator's Linear issue and the legacy behavior it cites. Write a table: original opcode(s) → mutated opcode(s) and when a site exists. Decide what is *not* a site (e.g. unary NEG, `IF_ACMP*` for boundary).
2. **Fixtures first.** Add Kotlin snippets via `compileKotlin` covering: the plain case, every opcode variant, long/double operands, static and instance methods, overloads, nested classes, and a negative case that must produce no site.
3. **File.** `mutkt-asm/src/main/kotlin/<package>/operators/<Name>Operator.kt`, one class implementing `Operator`:
   - `id` = stable `OperatorId` string (SCREAMING_SNAKE, matches docs and DSL).
   - `sites(method)` finds candidate instructions in order; ordinal is the index among this operator's sites in that method.
   - `apply(method, site)` rewrites the instruction list for exactly that site; maintain stack balance.
4. **Register** the operator in the single operators list (default-on set is MVP six; others opt-in until proven).
5. **Contract test** runs automatically for registered operators; make it pass, then add operator-specific behavior tests (loaded mutant returns/does something different).
6. **Docs.** Add a row to `docs/operators.md`: bytecode change, Kotlin before/after, known equivalent mutants.
7. **Checks.** `./gradlew :mutkt-asm:test ktlintCheck detekt`; then agent `mutkt-reviewer`.

## Rules
- No reference to other operators; if two operators emit identical bytes for some code, report it in the issue (the contract test asserts uniqueness across operators).
- Never mutate `<init>`, `<clinit>`, synthetic or bridge methods (framework skips them centrally; do not re-filter).
- Kotlin-specific operators detect Kotlin classes through the framework helper, not by re-scanning metadata themselves.
- Verify stack effects with `CheckClassAdapter`; do not rely on COMPUTE_FRAMES to hide errors.

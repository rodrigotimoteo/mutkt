---
name: mutkt-dev
description: Use when implementing any MutKt Linear task (ROD-n) in this repo. Defines the task flow, architecture guardrails, checks and definition of done.
---

# MutKt development flow

1. **Load the task.** Fetch the issue (Linear `get_issue`, plus `list_comments`). Confirm its blockers are done and read the project docs it references. Note the tier label (`agent:worker|implementer|deep`) and the milestone.
2. **Check scope.** If the issue needs a decision (`mk-decision`) that is still open, stop and ask. If it adds something no issue asks for, drop it (YAGNI).
3. **Place the code.** Pick the module from `AGENTS.md`'s table. Domain: pure Kotlin, no deps. Application: use cases and ports only. Adapters: implement a port; never import another adapter.
4. **Test first.** Write the failing test (see skill `mutkt-testing`), run it, see it fail for the right reason.
5. **Implement smallest.** Short functions (≤ 40 lines), files ≤ 300 lines, explicit visibility, immutable data classes, constructor injection, no singletons.
6. **Check.**
   ```bash
   ./gradlew :<module>:test ktlintCheck detekt
   ./gradlew build        # before reporting
   ```
7. **Review.** Run agent `mutkt-reviewer` on the diff for anything beyond a trivial change.
8. **Commit and push as you go.** Small commits that build and pass, message explains why + `ROD-n`; push after each. Work on a branch named after the issue, off `rebuild/v1`. A second concurrent task gets its own worktree (`git worktree add ../mutkt-rod-NN -b <branch> rebuild/v1`). Merge via PR into `rebuild/v1` only.
9. **Close the loop.** Update the Linear issue: status and one comment (changed / verified / not verified). Subagents never commit or push; the main session does after reading the diff.

## Guardrails
- Mutant ids include the method descriptor; domain objects never carry bytecode.
- Fail fast on invalid config at construction time with a clear message.
- No logging abstraction until a task needs one; progress goes through an injected lambda, output through injected `Appendable`/writers.
- Legacy code (`v0.3.3`) is a behavior reference only; every intentional difference gets noted in `docs/parity.md` once ROD-102 exists.
- Anything touching secrets, publishing, tagging or pushing is the owner's action.

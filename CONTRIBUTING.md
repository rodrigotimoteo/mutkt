# Contributing

1. Take an unblocked issue from the Linear project "MutKt Rebuild" (lowest milestone first).
2. Branch from `rebuild/v1`; name it after the issue (Linear's suggested branch name works). Commit and push as you go; several commits per issue are fine. Parallel tasks use separate `git worktree`s. Merge only via PR into `rebuild/v1`.
3. Follow [AGENTS.md](AGENTS.md): layering, size limits, test-first, no speculative features.
4. `./gradlew build` must pass locally; CI runs ktlint, detekt, tests (JDK 17 and 21) and the coverage gate.
5. Open a PR using the template; link the issue; describe what was verified and what was not.

Security issues: see [SECURITY.md](SECURITY.md). Conduct: see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

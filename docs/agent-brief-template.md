# Subagent brief template

Subagents start cold. Copy this, fill every field, paste into the Agent prompt.

```
GOAL: <one sentence; link ROD-n>
TIER: worker | implementer | deep   (from the issue label)
FILES IN SCOPE: <exact paths to create/edit; read-only references>
MUST NOT CHANGE: <modules, public API, other tasks' files>
KNOWN: <decisions, ADRs, blockers already done, relevant AGENTS.md rules>
DONE WHEN: <acceptance criteria from the issue, as a checklist>
CHECKS: <exact commands, e.g. ./gradlew :mutkt-asm:test ktlintCheck detekt>
REPORT (<= 250 words): files changed, behavior in 1-2 sentences, check results (pass/fail + failing test name), anything unverified, questions.
RULES: no commits, pushes, tags, publishing, secrets; stop and ask if the design looks wrong.
```

Tier guide: `worker` mechanical and fully specified; `implementer` scoped code + tests; `deep` design questions and hard bugs (write the design note in the issue first). Independent briefs may run in parallel only if their files do not overlap. The main session re-runs checks and reads the diff before accepting a report; `mutkt-reviewer` runs on every non-trivial change.

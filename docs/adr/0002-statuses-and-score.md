# 0002 Statuses and score

## Context

Mutation results need a fixed status set and one score formula (ROD-82).

## Decision

Statuses are `KILLED`, `SURVIVED`, `TIMEOUT`, `NO_COVERAGE` and `ERROR`. Score = (KILLED + TIMEOUT) / (total - ERROR). The score is null when nothing is scorable. Scores assume the baseline run of the tests on the original classes was green; a red baseline stops the run before any mutant is scored.

## Consequences

- `mutkt-core` domain `Status` and `Score` hold these rules.
- Reports (ADR 0003) use the same statuses and score.

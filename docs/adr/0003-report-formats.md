# 0003 Report formats

## Context

Mutation results need a quick terminal view, a shareable page and a machine-readable form. The format set is open in ROD-83.

## Decision

Ship three report formats behind the `ReportWriter` port: console, HTML, and JSON in the Stryker report schema.

## Consequences

- `mutkt-report` provides the console, HTML and JSON writers.
- The JSON output stays compatible with the Stryker report schema.
- Statuses and score follow ADR 0002.

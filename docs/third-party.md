# Third-party files

Vendored files, kept as downloaded from the npm registry (fetched once with `npm pack`).

| File | Source | Version | License | sha256 |
|---|---|---|---|---|
| `mutkt-report/src/main/resources/com/github/rodrigotimoteo/mutkt/report/mutation-test-elements.js` | `dist/mutation-test-elements.js` of npm `mutation-testing-elements`, inlined into the HTML report | 3.9.0 | Apache-2.0 (`mutation-testing-elements.LICENSE` beside it); bundled code listed below, notices in `THIRD-PARTY-NOTICES.txt` beside it | `751fb010242b0b44e32d84fe7fe0b9ff1da182823b94f59f5c52b001fcfc163b` |
| `mutkt-report/src/test/resources/mutation-testing-report-schema.json` | `src/mutation-testing-report-schema.json` of npm `mutation-testing-report-schema`; the JSON report is validated against it in tests only | 3.9.0 | Apache-2.0 | `404575e9264686dbf85c399f6531fdb489bebc3146e7e1d793b8083bc6a041ae` |

## Code bundled in `mutation-test-elements.js`

Audited from the runtime packages of `mutation-testing-elements` 3.9.0 (its `package.json`, checked against the bundle). Upstream ships no NOTICE file. Each license text was copied from the component's own npm package into `mutkt-report/src/main/resources/com/github/rodrigotimoteo/mutkt/report/THIRD-PARTY-NOTICES.txt`, which goes into the `mutkt-report` jar. Every generated HTML report has a comment that points to that file.

| Component | Version | License |
|---|---|---|
| mutation-testing-metrics, mutation-testing-real-time, mutation-testing-report-schema | 3.9.0 | Apache-2.0 (same project) |
| lit, lit-html, lit-element, @lit/reactive-element | 3.3.3, 3.3.x, 4.2.x, 2.1.x | BSD-3-Clause |
| @lit-labs/observers | 2.1.0 | BSD-3-Clause |
| prismjs | 1.30.0 | MIT |
| prism-svelte | 0.5.0 | MIT (declared in package.json; the package has no license file) |
| fuzziersort | 4.0.2 | MIT |
| anser | 2.3.5 | MIT |
| tailwindcss, @tailwindcss/forms (generated CSS) | 4.3.3, 0.5.11 | MIT |
| rxjs | 7.8.2 | Apache-2.0 |

The `.x` versions are transitive ranges of lit 3.3.3. The exact patch comes from the upstream lockfile, and the license text is the same across those patches.

To upgrade, repeat this audit, pack the new version, replace the files, update this table and rerun `./gradlew :mutkt-report:test`.

# Security Policy

## Reporting a Vulnerability

**Do NOT open a public issue for security vulnerabilities.**

Please report vulnerabilities privately via [GitHub Security Advisories](https://github.com/rodrigotimoteo/mutkt/security/advisories/new).

We will respond within 48 hours and work with you to understand and address the issue before any public disclosure.

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.2.x   | :white_check_mark: |
| 0.1.x   | :white_check_mark: |
| < 0.1   | :x:                |

## Security Measures

- **Dependency Scanning**: Regular vulnerability scans of dependencies
- **Code Signing**: GPG signing for published artifacts
- **SBOM Generation**: Software Bill of Materials for supply chain security

## Dependency Policy

We use [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/) to scan for known vulnerabilities in our dependencies.

Dependencies with a CVSS score >= 7.0 are flagged in the report (build continues).

## Supply Chain Security

- All releases are signed with GPG keys
- Dependencies are pinned to specific versions

## Best Practices

When using this library:

1. Keep dependencies updated
2. Use the latest stable version
3. Follow secure coding practices
4. Report any suspicious behavior

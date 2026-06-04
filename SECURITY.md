# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it by [opening an issue](https://github.com/rodrigotimoteo/mutkt/issues/new?template=bug_report.md).

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

Dependencies with a CVSS score >= 7.0 will fail the build.

## Supply Chain Security

- All releases are signed with GPG keys
- Dependencies are pinned to specific versions

## Best Practices

When using this library:

1. Keep dependencies updated
2. Use the latest stable version
3. Follow secure coding practices
4. Report any suspicious behavior

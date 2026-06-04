# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **Email**: security@example.com
2. **Include**: Description, steps to reproduce, potential impact
3. **Response**: Within 48 hours

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
- **Regular Audits**: Periodic security reviews

## Dependency Policy

We use [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/) to scan for known vulnerabilities in our dependencies.

Dependencies with a CVSS score >= 7.0 will fail the build.

## Supply Chain Security

- All releases are signed with GPG keys
- SBOMs are generated for each release
- Dependencies are pinned to specific versions
- Build reproducibility is maintained

## Security Updates

Security updates are released as patch versions (e.g., 0.2.1) and are announced via:

- GitHub Security Advisories
- Release notes
- Email notifications (for reported vulnerabilities)

## Best Practices

When using this library:

1. Keep dependencies updated
2. Use the latest stable version
3. Review SBOMs before integrating
4. Follow secure coding practices
5. Report any suspicious behavior

## Contact

For security-related inquiries, contact:

- Email: security@example.com
- GitHub: [Security Advisories](https://github.com/rodrigotimoteo/mutationKotlin/security/advisories)

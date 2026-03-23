# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 2.x     | :white_check_mark: |
| 1.x     | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in Karate v2, please report it responsibly.

### How to Report

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please use our [contact form](https://www.karatelabs.io/contact-us).

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested fixes (optional)

### What to Expect

- **Acknowledgment** within 48 hours
- **Initial assessment** within 7 days
- **Regular updates** on progress
- **Credit** in the security advisory (unless you prefer anonymity)

### Disclosure Policy

We follow coordinated disclosure:

1. Reporter submits vulnerability privately
2. We confirm and assess severity
3. We develop and test a fix
4. We release the fix and publish an advisory
5. Reporter may publish details after the fix is released

## Security Best Practices

When using Karate v2:

- Keep dependencies updated
- Review mock server configurations before exposing to networks
- Use environment variables for sensitive data in tests
- Follow the principle of least privilege for test credentials

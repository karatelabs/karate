# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 2.x     | :white_check_mark: |
| 1.x     | :x:                |

## Scope: read before reporting

Karate is a **testing framework**. Everything it ships (the test runner, the mock server,
`HttpServer` and server mode, and the CLI) is developer tooling for local machines, CI
and trusted internal networks. **None of it is designed or supported to face untrusted
or public clients.** The [user documentation](https://docs.karatelabs.io/extensions/test-doubles#security-and-trust-boundary)
says so explicitly.

A Karate feature, mock, `karate-config.js` or JS file is *trusted code*. By design it can
read files, call Java, run processes and make network calls with the privileges of the
JVM. That is the point of a test tool, not a flaw.

How we triage reports:

- **Not a vulnerability.** Anything that requires the attacker to author or modify a
  feature, config or JS file. The test HTTP client trusting all certificates by default
  (intentional, so tests can reach targets with self-signed certificates; configurable
  via `configure ssl`). Findings from automated or AI-driven scanning that have no
  working proof of concept.
- **Low severity at most.** Anything that requires an attacker to reach a Karate mock or
  server over the network, including further variants of
  [GHSA-2c85-rfcc-g74j](https://github.com/karatelabs/karate/security/advisories/GHSA-2c85-rfcc-g74j).
  Such a deployment is already outside the supported model, so we treat these as
  defense-in-depth hardening. We fix them in the next release, but we don't rate them
  as if Karate were an internet-facing server.

Hardening suggestions are welcome as regular GitHub issues or pull requests.

## Reporting a Vulnerability

If you discover a security vulnerability in Karate v2, please report it responsibly.

### How to Report

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, use GitHub's [private vulnerability reporting](https://github.com/karatelabs/karate/security/advisories/new),
or email **security@karatelabs.io**.

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
- Run mock servers and `HttpServer` on trusted networks only; never expose them to untrusted clients
- Use environment variables for sensitive data in tests
- Follow the principle of least privilege for test credentials

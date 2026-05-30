# Karate v2

Start with [docs/DESIGN.md](./docs/DESIGN.md) — the primary architecture reference for the codebase (and the deep-dive docs it links).

## Conventions

- **No issue/PR numbers in source.** Describe the behavior or rationale instead of citing a GitHub issue — in comments, test names, and Gherkin `Feature:`/`Scenario:` names. Load-bearing third-party tracker links are fine. Not a hard failure, just the house style.
- **Do reference the issue in commit messages.** When a commit fixes a tracked issue, include `fixes #123` (or a plain `#123` mention) in the body for backlinking. This does *not* auto-close — GitHub auto-close is disabled for this repo. Practice is to leave the issue open until the Maven Central release is made, then close it.

# Project conventions

## No issue/PR numbers in code

Do not name or refer to this project's GitHub issue or PR numbers anywhere in the
source — not in comments, Javadoc, test method names, `@DisplayName`s, Gherkin
`Feature:`/`Scenario:` names, or string literals. Issue numbers rot: they're
opaque to a future reader and the tracker may move or disappear.

Describe the **behavior or rationale** instead.

- Bad:  `// Issue #2840: synthetic step absorbs config-time output`
- Bad:  `void testIssue2515() { ... }`
- Bad:  `Feature: Issue 2780`
- Good: `// a failed callSingle still surfaces its steps in the report`
- Good: `void testEqualsArrayContainsOnlyWithNestedObjects() { ... }`
- Good: `Feature: config with toLocaleDateString`

Commit messages may reference issues (`fixes #1234`) — that's git metadata, not code.

Links to a **third-party** dependency's issue tracker are acceptable when the
number is load-bearing for the reader (e.g. documenting an upstream bug/workaround).

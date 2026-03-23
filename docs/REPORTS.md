# Karate v2 Reports

> See also: [RUNTIME.md](./RUNTIME.md) | [CLI.md](./CLI.md) | [LOGGING.md](./LOGGING.md) | [EVENTS.md](./EVENTS.md)

This document describes the reporting architecture for Karate v2, including the Karate JSON format (source of truth), JSONL event streaming, Cucumber JSON generation, and HTML report rendering.

---

## Implementation Status

> **This is the primary document for remaining report system work.** The event system ([EVENTS.md](./EVENTS.md)) is complete.

### Completed ✅

| Component | Description |
|-----------|-------------|
| Event System (EVENTS.md Phases 1-5) | `RunEvent`, `RunListener`, `RunListenerFactory` interfaces |
| JSONL Event Stream | `JsonLinesEventWriter` writes `karate-events.jsonl` with standard envelope |
| Report Aggregation | `HtmlReport.aggregate()` and `HtmlReportWriter.parseJsonLines()` parse new format |
| HTML Report Generation | `HtmlReportWriter` generates HTML from JSONL or runtime results |
| **Phase 1: JSON Consolidation** | Renamed `toKarateJson()` to `toJson()`, standardized field names (`durationMillis`, `scenarioResults`, `stepResults`) |
| **Phase 2: HTML Template Updates** | Updated Alpine.js templates for new JSON structure, fixed loop call results, timeline generation |
| **Phase 3: Cucumber JSON** | `CucumberJsonReportListener` generates per-feature Cucumber JSON files asynchronously |
| **JUnit XML** | `JunitXmlReportListener` generates per-feature JUnit XML files asynchronously |

### Deferred

| Component | Description |
|-----------|-------------|
| PROGRESS Events | Periodic progress updates and console display |
| JSONL → FeatureResult | `FeatureResult.fromJson()` to reconstitute results from JSONL for offline report generation |

### TODO: Report Rendering Test Cases

Add the following test cases to `HtmlReportWriterTest.java` for nested call rendering:

| Pattern | Description |
|---------|-------------|
| `karate.call()` from JS function | Test that `def fun = function(){ return karate.call('file.feature') }; call fun` shows nested call in report |
| JS call in Background | Test that JS-initiated calls in Background section render correctly |
| Nested call hierarchy | Test multi-level `call` → `karate.call()` → `call` chains render properly |

Reference: V1 `call-js.feature` tests whether JS-initiated calls show up in the report.

---

## Overview

Karate v2 uses a unified reporting architecture:

```
Test Execution
      ↓
FeatureResult.toJson()  ← Single canonical format
      ↓
  ┌───┼───────┬──────────┐
  ↓   ↓       ↓          ↓
JSONL HTML  Cucumber   JUnit
           JSON        XML
```

**Key principles:**
- **Single source of truth**: `FeatureResult.toJson()` is the canonical format for all reports
- **Happy path optimization**: During test execution, report listeners receive `FeatureResult` objects directly (no serialization/deserialization overhead)
- **Async generation**: Cucumber JSON, JUnit XML, and HTML reports are generated asynchronously via `ResultListener` implementations
- **Future offline processing**: JSONL contains the same `FeatureResult.toJson()` data, enabling future `FeatureResult.fromJson()` reconstitution for offline report generation

### Core Classes

| Class | Description |
|-------|-------------|
| `HtmlReportListener` | Async HTML report generation (default), implements `ResultListener` |
| `HtmlReportWriter` | HTML report generation with inlined JSON |
| `JsonLinesEventWriter` | JSONL event streaming (opt-in via `.outputJsonLines(true)`) |
| `CucumberJsonReportListener` | Async per-feature Cucumber JSON (opt-in via `.outputCucumberJson(true)`), writes to `cucumber-json/` |
| `CucumberJsonWriter` | Cucumber JSON conversion from `FeatureResult` |
| `JunitXmlReportListener` | Async per-feature JUnit XML (opt-in via `.outputJunitXml(true)`), writes to `junit-xml/` |
| `JunitXmlWriter` | JUnit XML conversion from `FeatureResult` |

### Runner API

```java
// Default: HTML reports generated automatically
Runner.path("features/")
    .parallel(5);

// Opt-in JSON Lines for aggregation/streaming
Runner.path("features/")
    .outputJsonLines(true)
    .parallel(5);

// Disable HTML reports
Runner.path("features/")
    .outputHtmlReport(false)
    .parallel(5);

// Generate Cucumber JSON (per-feature files, async)
Runner.path("features/")
    .outputCucumberJson(true)
    .parallel(5);
```

---

## Karate JSON Format

The karate JSON format is the **single source of truth** for all report generation. All result classes implement `toKarateJson()` methods that produce this canonical format.

### FeatureResult

```json
{
  "name": "User Management",
  "description": "Tests for user CRUD operations",
  "durationMillis": 1234,
  "passedCount": 2,
  "failedCount": 0,
  "packageQualifiedName": "features.users",
  "relativePath": "features/users.feature",
  "resultDate": "2024-12-23 10:30:00 AM",
  "prefixedPath": "classpath:features/users.feature",
  "loopIndex": -1,
  "callDepth": 0,
  "line": 1,
  "id": "features_users_feature",
  "tags": [{ "name": "@api", "line": 1 }],
  "scenarioResults": [...]
}
```

| Field | Description |
|-------|-------------|
| `name` | Feature name from the feature file |
| `description` | Feature description |
| `durationMillis` | Total execution time in milliseconds |
| `passedCount` | Number of passed scenarios |
| `failedCount` | Number of failed scenarios |
| `packageQualifiedName` | Dot-separated path (e.g., `features.users`) |
| `relativePath` | File path relative to classpath root |
| `resultDate` | Execution timestamp in "yyyy-MM-dd HH:mm:ss a" format |
| `prefixedPath` | Full prefixed path (e.g., `classpath:features/users.feature`) |
| `loopIndex` | Loop index for repeated calls (-1 if not looped) |
| `callDepth` | Nesting depth (0 for top-level features) |
| `callArg` | (Optional) Arguments passed to called feature |
| `line` | Feature line number |
| `id` | Unique feature ID |
| `tags` | Array of tag objects with name and line |
| `scenarioResults` | Array of ScenarioResult objects |

### ScenarioResult

```json
{
  "name": "Create user",
  "description": "",
  "line": 5,
  "durationMillis": 500,
  "failed": false,
  "refId": "[1:5]",
  "sectionIndex": 1,
  "exampleIndex": -1,
  "executorName": "ForkJoinPool-1-worker-1",
  "startTime": 1703347200000,
  "endTime": 1703347200500,
  "id": "create-user",
  "tags": [{ "name": "@smoke", "line": 4 }],
  "error": null,
  "exampleData": null,
  "stepResults": [...]
}
```

| Field | Description |
|-------|-------------|
| `name` | Scenario name |
| `description` | Scenario description |
| `line` | Source line number |
| `durationMillis` | Execution time in milliseconds |
| `failed` | Boolean indicating failure |
| `refId` | Scenario reference ID: `[section.exampleIndex:line]` |
| `sectionIndex` | 1-based section index within feature |
| `exampleIndex` | Example row index for outlines (-1 if not outline) |
| `exampleData` | (Optional) Example row data for scenario outlines |
| `executorName` | Thread name that executed this scenario |
| `startTime` | Start timestamp (epoch milliseconds) |
| `endTime` | End timestamp (epoch milliseconds) |
| `id` | Unique scenario ID |
| `tags` | Array of tag objects - **effective tags** (merged feature + scenario) |
| `error` | (Optional) Error message if failed |
| `stepResults` | Array of StepResult objects |

### StepResult

StepResult uses **nested `step` and `result` objects** (v1 style):

```json
{
  "step": {
    "index": 0,
    "line": 6,
    "prefix": "*",
    "text": "url baseUrl",
    "background": false,
    "comments": ["# Setup URL"],
    "docString": null,
    "table": null
  },
  "result": {
    "status": "passed",
    "millis": 1,
    "nanos": 1000000,
    "startTime": 1703347200000,
    "endTime": 1703347200001,
    "errorMessage": null,
    "aborted": false
  },
  "hidden": false,
  "stepLog": "...",
  "embeds": [...],
  "callResults": [...]
}
```

#### step object

| Field | Description |
|-------|-------------|
| `index` | Step index within scenario |
| `line` | Source line number |
| `endLine` | (Optional) End line if multi-line |
| `prefix` | Gherkin prefix: `*`, `Given`, `When`, `Then`, `And`, `But` |
| `text` | Step text AFTER prefix, **trimmed** (e.g., `def x = 1`) |
| `background` | (Optional) True if background step |
| `comments` | (Optional) Array of comment strings |
| `docString` | (Optional) Multi-line doc string |
| `table` | (Optional) Data table rows |

**Note:** `text` is trimmed, so JS can use `text.startsWith('def ')` or `text.startsWith('url ')` for keyword detection.

#### result object

| Field | Description |
|-------|-------------|
| `status` | `"passed"`, `"failed"`, or `"skipped"` |
| `millis` | Duration in milliseconds (convenience) |
| `nanos` | Duration in nanoseconds (actual) |
| `startTime` | Start timestamp (epoch milliseconds) |
| `endTime` | End timestamp (epoch milliseconds) |
| `errorMessage` | (Optional) Error message if failed |
| `aborted` | (Optional) True if execution was aborted |

#### Top-level StepResult fields

| Field | Description |
|-------|-------------|
| `hidden` | (Optional) True if step should be hidden in reports |
| `stepLog` | Execution log output (HTTP, print, etc.) |
| `embeds` | Array of embedded content (HTML, images, etc.) |
| `callResults` | Array of FeatureResult for called features |

### Nested Call Hierarchy

When a step calls another feature, the called feature's results are nested in `callResults`:

```json
{
  "step": {
    "index": 2,
    "line": 10,
    "prefix": "*",
    "text": "call read('helper.feature')"
  },
  "result": {
    "status": "passed",
    "nanos": 12345678,
    "millis": 12,
    "startTime": 1703347200000,
    "endTime": 1703347200012
  },
  "callResults": [
    {
      "name": "Helper Feature",
      "description": "",
      "durationMillis": 12,
      "passedCount": 1,
      "failedCount": 0,
      "packageQualifiedName": "features.helper",
      "relativePath": "features/helper.feature",
      "resultDate": "2024-12-23 10:30:00 AM",
      "prefixedPath": "classpath:features/helper.feature",
      "callArg": { "param1": "value1" },
      "loopIndex": -1,
      "callDepth": 1,
      "scenarioResults": [...]
    }
  ]
}
```

### Embeds

Steps can include embedded content that appears in HTML reports:

```json
{
  "embeds": [
    {
      "mime_type": "text/html",
      "data": "PCFET0NUWVBFIGh0bWw+Li4u",
      "name": "report.html"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `mime_type` | MIME type (e.g., `text/html`, `image/png`) |
| `data` | Base64-encoded content |
| `name` | (Optional) Name/label |

The `doc` keyword automatically embeds rendered HTML templates:

```cucumber
* doc 'user-report.html'
```

Custom embeds via `LogContext.get().embed(data, mimeType)`.

---

## JSONL Event Stream

Karate v2 writes a unified event stream to `karate-events.jsonl`. This single file serves both **live progress monitoring** and **report generation**:

- **During execution:** Lightweight events (enter/exit, progress) for real-time UIs
- **On feature completion:** Full feature result JSON for report generation
- **Post-execution:** "Replay" the stream to generate HTML/Cucumber reports

See [EVENTS.md](./EVENTS.md) for the complete event system architecture.

### Standard Event Envelope

All events share a common structure for consistency and future-proofing:

```json
{
  "type": "EVENT_TYPE",
  "ts": 1703500000200,
  "threadId": "worker-1",
  "data": { ... }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Event type (UPPER_CASE) |
| `ts` | long | Epoch milliseconds (UTC, timezone-agnostic) |
| `threadId` | string? | Thread identifier (nullable for suite-level events) |
| `data` | object | Event-specific payload |

**Note:** All `ts` values are Unix epoch milliseconds (milliseconds since 1970-01-01 00:00:00 UTC). Consumers can convert to any local timezone.

### Core Event Types

#### SUITE_ENTER

```json
{
  "type": "SUITE_ENTER",
  "ts": 1703500000000,
  "threadId": null,
  "data": {
    "version": "2.0.0",
    "schemaVersion": "1",
    "env": "dev",
    "threads": 5,
    "tags": "@smoke",
    "paths": ["features/"]
  }
}
```

#### SUITE_EXIT

Contains summary statistics and `featureSummary` for rendering `karate-summary.html`:

```json
{
  "type": "SUITE_EXIT",
  "ts": 1703500010000,
  "threadId": null,
  "data": {
    "elapsedMs": 10000,
    "threadTimeMs": 45000,
    "efficiency": 0.90,
    "resultDate": "2024-12-23 10:30:00 AM",
    "featuresPassed": 10,
    "featuresFailed": 2,
    "featuresSkipped": 1,
    "scenariosPassed": 42,
    "scenariosFailed": 3,
    "featureSummary": [
      {
        "path": "features/users.feature",
        "name": "User Management",
        "passed": true,
        "durationMs": 500,
        "passedCount": 3,
        "failedCount": 0,
        "scenarioCount": 3
      }
    ]
  }
}
```

#### FEATURE_ENTER / FEATURE_EXIT

```json
{
  "type": "FEATURE_ENTER",
  "ts": 1703500000010,
  "threadId": "worker-1",
  "data": {
    "path": "features/users.feature",
    "name": "User Management",
    "line": 1
  }
}
```

`FEATURE_EXIT` contains full `toKarateJson()` output - the **source of truth** for reports:

```json
{
  "type": "FEATURE_EXIT",
  "ts": 1703500000200,
  "threadId": "worker-1",
  "data": {
    ...full toKarateJson() output...
  }
}
```

#### SCENARIO_ENTER / SCENARIO_EXIT

Key for IDE test runners (VS Code, IntelliJ):

```json
{
  "type": "SCENARIO_ENTER",
  "ts": 1703500000020,
  "threadId": "worker-1",
  "data": {
    "feature": "features/users.feature",
    "name": "Create user",
    "line": 5,
    "refId": "[1:5]",
    "tags": ["@smoke", "@api"]
  }
}

{
  "type": "SCENARIO_EXIT",
  "ts": 1703500000100,
  "threadId": "worker-1",
  "data": {
    "feature": "features/users.feature",
    "name": "Create user",
    "line": 5,
    "passed": true,
    "durationMs": 80
  }
}
```

#### PROGRESS

Periodic updates for live dashboards:

```json
{
  "type": "PROGRESS",
  "ts": 1703500005000,
  "threadId": null,
  "data": {
    "completed": 15,
    "total": 42,
    "percent": 35,
    "elapsedMs": 5000
  }
}
```

#### ERROR

Explicit error capture:

```json
{
  "type": "ERROR",
  "ts": 1703500000080,
  "threadId": "worker-1",
  "data": {
    "feature": "features/users.feature",
    "scenario": "Create user",
    "line": 8,
    "message": "status code was: 500, expected: 201",
    "type": "AssertionError"
  }
}
```

### Future Event Types

Reserved for future use:

| Type | Purpose |
|------|---------|
| `HTTP` | HTTP request/response capture with cURL export |
| `SCRIPT_ENTER` / `SCRIPT_EXIT` | JavaScript test execution (`.karate.js`) |
| `MOCK_REQUEST` | Mock server request handling |
| `CALL_ENTER` / `CALL_EXIT` | Feature call tracking (opt-in) |
| `STEP_ENTER` / `STEP_EXIT` | Step-level events (opt-in debug) |
| `RETRY` | Scenario retry attempts |
| `ABORT` | Execution aborted |

#### HTTP Event (Future)

```json
{
  "type": "HTTP",
  "ts": 1703500000050,
  "threadId": "worker-1",
  "data": {
    "feature": "features/users.feature",
    "scenario": "Create user",
    "scenarioLine": 5,
    "stepLine": 8,
    "request": {
      "method": "POST",
      "url": "https://api.example.com/users",
      "headers": { "Content-Type": "application/json" },
      "body": { "name": "John" }
    },
    "response": {
      "status": 201,
      "headers": { "Content-Type": "application/json" },
      "body": { "id": 123, "name": "John" },
      "bodySize": 45
    },
    "durationMs": 150,
    "curl": "curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -d '{\"name\":\"John\"}'"
  }
}
```

### Schema Versioning

The `schemaVersion` in `SUITE_ENTER.data` allows consumers to handle format changes. Increment when making breaking changes.

### Notes

- **Step events not default:** Reduces event volume. Full step details in `FEATURE_EXIT` via `toKarateJson()`.
- **Tag filtering:** `FEATURE_EXIT` contains only executed scenarios (those matching tag filters).
- **`threadId` as string:** Allows flexibility for future thread naming schemes.

### Configuration

```java
Runner.path("features/")
    .outputJsonLines(true)  // enables karate-events.jsonl
    .parallel(5);
```

Output file: `target/karate-reports/karate-events.jsonl`

### Use Cases

- **Report aggregation** across multiple test runs
- **Live progress dashboards** during execution
- **Cucumber JSON generation** (post-processing `FEATURE_EXIT` events)
- **HTTP traffic analysis** (future - via `HTTP` events)
- **External integrations** and custom tooling
- **Execution replay** for debugging and analysis

---

## Cucumber JSON

Cucumber JSON is generated **per-feature** as each feature completes, using the same async pattern as HTML reports. The format is **final and must not change** for compatibility with third-party tools.

### Generation

```
FeatureResult (on feature completion)
        ↓
CucumberJsonReportListener.onFeatureEnd()  (async)
        ↓
CucumberJsonWriter.writeFeature()
        ↓
{packageQualifiedName}.json  (e.g., features.users.json)
```

### Output Files

Each feature produces a separate Cucumber JSON file named after its `packageQualifiedName`:

```
target/karate-reports/
├── features.users.json       # From features/users.feature
├── features.orders.json      # From features/orders.feature
└── api.auth.login.json       # From api/auth/login.feature
```

Each file contains an array with a single feature object (standard Cucumber JSON format).

### Conversion Mapping

| Karate JSON | Cucumber JSON |
|-------------|---------------|
| `name` | `name` |
| `relativePath` | `uri` |
| `scenarioResults` | `elements` (with background interleaved) |
| `stepResults` | `steps` |
| `step.prefix` | `keyword` |
| `step.text` | `name` |
| `result.nanos` | `result.duration` |
| `stepLog` | `doc_string.value` |
| `embeds` | `embeddings` |
| Nested `callResults` | Flatten with `>` prefix on keywords |

### Cucumber JSON Format

```json
{
  "line": 2,
  "elements": [...],
  "name": "path/to/feature.feature",
  "description": "feature name\ndescription",
  "id": "feature-id",
  "keyword": "Feature",
  "uri": "path/to/feature.feature",
  "tags": [{ "name": "@tag", "line": 1 }]
}
```

**Key requirements:**
- Background sections interleaved before each scenario
- Scenario Outline examples as separate scenarios with `keyword: "Scenario Outline"`
- Called feature steps flattened with `> ` prefix on keyword
- `start_timestamp` in ISO-8601 format
- `duration` in nanoseconds
- Embeddings with `data` (base64) and `mime_type`
- Dummy `match` object: `{ "location": "karate", "arguments": [] }`

---

## HTML Reports

### Architecture

HTML reports use **client-side rendering with Alpine.js**. JSON data is inlined in the HTML file:

```html
<!DOCTYPE html>
<html>
<head>
  <link rel="stylesheet" href="res/bootstrap.min.css">
  <script src="res/alpine.min.js" defer></script>
</head>
<body x-data="reportData()">
  <!-- Alpine.js renders the report -->
</body>
<script type="application/json" id="karate-data">
  { /* inlined karate JSON */ }
</script>
<script>
  function reportData() {
    return {
      data: JSON.parse(document.getElementById('karate-data').textContent),
      // Alpine reactive state
    }
  }
</script>
</html>
```

**Benefits:**
- **Minimal I/O** - One HTML file per feature
- **No server required** - Works with `file://` protocol
- **Rich interactivity** - Search, filter, scroll sync all in client JS
- **Smaller total size** - JSON + template < pre-rendered HTML

### Output Structure

```
target/karate-reports/
├── index.html                    # Redirect to summary
├── karate-summary.html           # Summary view
├── karate-timeline.html          # Gantt-style timeline
├── feature-html/                 # Per-feature HTML reports
│   ├── users.list.html
│   └── orders.create.html
├── karate-json/                  # JSON data (opt-in)
│   └── karate-events.jsonl       # JSONL event stream (opt-in via .outputJsonLines(true))
├── junit-xml/                    # JUnit XML reports (optional)
│   ├── users.list.xml
│   └── orders.create.xml
├── cucumber-json/                # Cucumber JSON reports (optional)
│   ├── users.list.json
│   └── orders.create.json
└── res/
    ├── bootstrap.min.css
    ├── alpine.min.js
    └── karate-report.css
```

### Report Views

**Summary Page (`karate-summary.html`):**
- Overview of all features with pass/fail counts
- Sortable table columns (client-side)
- Tag filter chips for scenario-level filtering
- Expandable feature rows

**Feature Page (`feature-html/*.html`):**
- Left sidebar with scenario navigation
- Scenarios displayed with RefId format: `[section.exampleIndex:line]`
- Step rows with color-coded status (green=pass, red=fail, yellow=skip)
- Expandable logs on step click
- **Nested call display** - `callResults` shown as expandable hierarchy
- Dark mode theme toggle with localStorage persistence

**Timeline Page (`karate-timeline.html`):**
- Gantt-style visualization of parallel test execution
- Thread-based lanes showing concurrent execution
- Scenario-level granularity with pass/fail coloring
- Hover tooltips with timing details

### Nested Call Display

Call steps display nested scenario results from called features:

```
▼ * call read('helper.feature')                    [PASS] [100ms]
    ▼ Scenario: Helper scenario                    [PASS] [25ms]
        * print 'from helper'                      [PASS] [1ms]
```

The HTML template renders `callResults` as an expandable/collapsible tree.

---

## Memory-Efficient Generation

The `HtmlReportListener` writes feature HTML files asynchronously as each feature completes:

```
Test Execution
    │
HtmlReportListener (default)
    ├── onFeatureEnd() → Queue feature HTML to executor (async)
    │                  → Collect feature data using toKarateJson()
    └── onSuiteEnd()   → Write karate-summary.html + karate-timeline.html
                       → Wait for executor to finish
                       → Copy static resources
```

```java
@Override
public void onFeatureEnd(FeatureResult result) {
    result.sortScenarioResults();  // deterministic ordering
    featureMaps.add(result.toKarateJson());  // canonical format
    executor.submit(() -> writeFeatureHtml(result));  // async
}
```

---

## Report Aggregation

Merge reports from multiple test runs:

```java
HtmlReport.aggregate()
    .json("target/run1/karate-json/karate-events.jsonl")
    .json("target/run2/karate-json/karate-events.jsonl")
    .outputDir("target/combined-report")
    .generate();
```

**CLI:**
```bash
karate report --aggregate target/run1,target/run2 --output target/combined
```

---

## Template Customization

### Template Resolution Order

```
user templates (--templates dir)
  ↓ fallback
classpath:karate-report-templates/  (commercial JAR)
  ↓ fallback
classpath:io/karatelabs/output/  (built-in)
```

### Custom Templates

```java
Runner.path("features/")
    .outputHtmlReport(true)
    .reportTemplates("src/custom-templates/")
    .parallel(5);
```

### Variable Injection

```java
Runner.path("features/")
    .reportVariable("company", "Acme Corp")
    .reportVariable("logo", "classpath:branding/logo.png")
    .parallel(5);
```

---

## Configure Report

Control report verbosity and content via `configure report`:

| Option | Default | Description |
|--------|---------|-------------|
| `logLevel` | `info` | Min log level for reports: `trace`, `debug`, `info`, `warn`, `error` |

**Usage:**
```javascript
// In karate-config.js
karate.configure('report', { logLevel: 'debug' });

// In a feature file
* configure report = { logLevel: 'warn' }
```

See [LOGGING.md](./LOGGING.md) for more details on log level filtering.

---

## Local Development Guide

### Quick Start

**Run the dev test to generate reports:**

```bash
cd karate-core

# Run the dev test (outputs to target/karate-report-dev/)
mvn test -Dtest=HtmlReportWriterTest#testHtmlReportGeneration -q

# Open in browser
open target/karate-report-dev/karate-summary.html
```

**Alternative - Use CLI directly:**

```bash
mvn compile -q && mvn exec:java \
  -Dexec.mainClass="io.karatelabs.Main" \
  -Dexec.args="run -T 3 src/test/resources/io/karatelabs/report"

open target/karate-reports/karate-summary.html
```

### Key Files

| File | Purpose |
|------|---------|
| `src/main/resources/io/karatelabs/output/karate-summary.html` | Summary page template |
| `src/main/resources/io/karatelabs/output/karate-feature.html` | Feature page template |
| `src/main/resources/io/karatelabs/output/karate-timeline.html` | Timeline page template |
| `src/main/resources/io/karatelabs/output/res/` | Static resources (CSS, JS, images) |
| `src/main/java/io/karatelabs/output/HtmlReportWriter.java` | Java code that builds JSON data |
| `src/main/java/io/karatelabs/output/HtmlReportListener.java` | Async HTML report generation listener |
| `src/main/java/io/karatelabs/output/CucumberJsonWriter.java` | Cucumber JSON conversion logic |
| `src/main/java/io/karatelabs/output/CucumberJsonReportListener.java` | Async Cucumber JSON generation listener |

### Template Architecture

Templates use **Alpine.js** for reactivity and **Bootstrap 5** for styling:

```html
<body x-data="reportData()">
  <!-- Alpine.js bindings -->
  <div x-show="condition" x-text="data.value"></div>
</body>

<script id="karate-data" type="application/json">/* KARATE_DATA */</script>
<script>
  function reportData() {
    const data = JSON.parse(document.getElementById('karate-data').textContent);
    return { data, /* reactive state */ };
  }
</script>
```

The `/* KARATE_DATA */` placeholder is replaced with actual JSON by `HtmlReportWriter.inlineJson()`.

---

## Test Coverage

| Test Class | Coverage |
|------------|----------|
| `HtmlReportListenerTest` | Async HTML report generation |
| `JsonLinesReportListenerTest` | JSON Lines streaming |
| `HtmlReportWriterTest` | HTML report generation and aggregation |
| `CucumberJsonWriterTest` | Per-feature Cucumber JSON generation |

---

## Design Decisions

This section captures all design decisions for the v2 report system refactoring.

### Core Decisions

1. **Single format**: Use `toKarateJson()` everywhere, remove `toMap()` methods
2. **Call hierarchy**: V1 fields (`callArg`, `loopIndex`, `callDepth`) on FeatureResult + nested `callResults` in StepResult
3. **Step structure**: V1 nested `step: {...}` and `result: {...}` objects
4. **Step text**: V1 style - `prefix` and `text` separate, `text` is **trimmed** (use `text.startsWith('def ')` in JS)
5. **No keyword field**: Use `text.startsWith()` for keyword detection instead
6. **HTML reports**: Focus on nested call display (`callResults` hierarchy)

### JSONL Event Stream Decisions

7. **Unified JSONL**: Single `karate-events.jsonl` file for all purposes (live progress + report generation)
8. **Event envelope**: Standard `{type, ts, threadId, data}` structure for all events
9. **threadId**: String type (nullable) for flexibility in future thread naming
10. **Schema versioning**: `schemaVersion` in `SUITE_ENTER.data` for forward compatibility
11. **FEATURE_EXIT content**: Contains full `toKarateJson()` output - the source of truth for reports
12. **SUITE_EXIT content**: Includes V1-aligned stats (`elapsedMs`, `threadTimeMs`, `efficiency`, `resultDate`) + `featureSummary` array for HTML summary
13. **Step events**: Not in JSONL by default (reduces volume); step details in `FEATURE_EXIT` via `toKarateJson()`
14. **Tag filtering**: `FEATURE_EXIT` contains only executed scenarios (those matching tag filters)
15. **Scenario tags**: Use `getTagsEffective()` for merged feature+scenario tags (V1 behavior)
16. **Future events**: Reserved types for `HTTP`, `SCRIPT_*`, `MOCK_REQUEST`, `CALL_*`, `STEP_*`, `RETRY`, `ABORT`

### V1 Alignment

17. **Cucumber JSON**: Final format, must not change - generate from JSONL post-processing
18. **Karate JSON**: Align with V1 field names while preserving V2 nested call hierarchy

---

## Implementation Plan

This section documents the refactoring needed to align v2 with v1 karate JSON format.

> **Dependency order:** Phase 1 (JSON) → Phase 2 (HTML) → Phase 3 (Cucumber)
>
> Cucumber JSON generation depends on `toKarateJson()` producing V1-compatible output.

---

### Phase 1: Consolidate to Single Format ✅

**Goal:** Remove `toMap()`, use `toJson()` everywhere.

**Status:** COMPLETE

#### Files to Modify

| File | Changes |
|------|---------|
| `FeatureResult.java` | Remove `toMap()`, update `toKarateJson()` to include all v1 fields |
| `ScenarioResult.java` | Remove `toMap()`, update `toKarateJson()` to include all v1 fields |
| `StepResult.java` | Remove `toMap()`, restructure to v1 nested `step`/`result` format |
| `SuiteResult.java` | Align `toKarateJson()` with v1 `Results.toKarateJson()` |
| `HtmlReportWriter.java` | Use `toKarateJson()` instead of `toMap()` |

#### FeatureResult Changes

Add these fields to `toKarateJson()`:
- `durationMillis` - total execution time
- `passedCount` - number of passed scenarios
- `failedCount` - number of failed scenarios
- `packageQualifiedName` - dot-separated path
- `resultDate` - execution timestamp
- `prefixedPath` - full prefixed path
- `callArg` - (for called features) arguments passed
- `loopIndex` - loop index (-1 if not looped)
- `callDepth` - nesting depth (0 for top-level)

Rename:
- `elements` → `scenarioResults`

Remove:
- `uri` (redundant with `relativePath`)
- `result` summary object (use individual fields)

#### ScenarioResult Changes

Add these fields:
- `durationMillis` - execution time
- `failed` - boolean
- `refId` - scenario reference ID
- `sectionIndex` - 1-based section index
- `exampleIndex` - example row index (-1 if not outline)
- `exampleData` - (for outlines) example row data
- `executorName` - thread name
- `startTime` / `endTime` - epoch milliseconds
- `error` - error message if failed

Rename:
- `steps` → `stepResults`

Fix:
- `tags` - use `scenario.getTagsEffective()` instead of `scenario.getTags()` (V1 uses effective/inherited tags)

Remove:
- `result` summary object (use individual fields)

#### StepResult Changes

Restructure to nested `step` and `result` objects:

```java
public Map<String, Object> toKarateJson() {
    Map<String, Object> map = new LinkedHashMap<>();

    // step object
    Map<String, Object> stepMap = new LinkedHashMap<>();
    if (step.isBackground()) stepMap.put("background", true);
    stepMap.put("index", step.getIndex());
    stepMap.put("line", step.getLine());
    if (step.getEndLine() != step.getLine()) stepMap.put("endLine", step.getEndLine());
    if (step.getComments() != null) stepMap.put("comments", step.getComments());
    stepMap.put("prefix", step.getPrefix());
    stepMap.put("text", step.getText().trim());  // trimmed!
    if (step.getDocString() != null) stepMap.put("docString", step.getDocString());
    if (step.getTable() != null) stepMap.put("table", step.getTable().toKarateJson());
    map.put("step", stepMap);

    // result object
    Map<String, Object> resultMap = new LinkedHashMap<>();
    resultMap.put("status", status.name().toLowerCase());
    resultMap.put("millis", durationNanos / 1_000_000);
    resultMap.put("nanos", durationNanos);
    resultMap.put("startTime", startTime);
    resultMap.put("endTime", endTime);
    if (error != null) resultMap.put("errorMessage", error.getMessage());
    if (aborted) resultMap.put("aborted", true);
    map.put("result", resultMap);

    // top-level fields
    if (hidden) map.put("hidden", true);
    if (log != null && !log.isEmpty()) map.put("stepLog", log);
    if (embeds != null && !embeds.isEmpty()) {
        map.put("embeds", embeds.stream().map(Embed::toMap).collect(toList()));
    }
    if (nestedResults != null && !nestedResults.isEmpty()) {
        map.put("callResults", nestedResults.stream()
            .map(FeatureResult::toKarateJson).collect(toList()));
    }

    return map;
}
```

#### SuiteResult Changes

Align `SuiteResult.toKarateJson()` with V1 `Results.toKarateJson()`:

**Current V2:**
```json
{
  "features": [...full FeatureResult objects...],
  "summary": {
    "feature_count": 12,
    "feature_passed": 10,
    "feature_failed": 2,
    "scenario_count": 45,
    "scenario_passed": 42,
    "scenario_failed": 3,
    "duration_millis": 12345,
    "status": "failed"
  }
}
```

**Target (V1-aligned):**
```json
{
  "version": "2.0.0",
  "env": "dev",
  "threads": 5,
  "featuresPassed": 10,
  "featuresFailed": 2,
  "featuresSkipped": 1,
  "scenariosPassed": 42,
  "scenariosFailed": 3,
  "elapsedTime": 12345.0,
  "totalTime": 45678.0,
  "efficiency": 0.85,
  "resultDate": "2024-12-23 10:30:00 AM",
  "featureSummary": [
    {
      "path": "features/users.feature",
      "name": "User Management",
      "passed": true,
      "durationMs": 500,
      "passedCount": 3,
      "failedCount": 0,
      "scenarioCount": 3
    }
  ]
}
```

**Changes:**
- Add: `version`, `env`, `threads`, `featuresSkipped`, `elapsedTime`, `totalTime`, `efficiency`, `resultDate`
- Rename: `feature_passed` → `featuresPassed` (camelCase)
- Add: `featureSummary` array with lightweight feature info (for `karate-summary.html`)
- Remove: nested `summary` object (flatten to top level)

### Phase 2: Update HTML Templates ✅

**Goal:** Update Alpine.js templates to use new JSON structure from Phase 1.

**Status:** COMPLETE

Update Alpine.js templates to use new JSON structure:

| Current | New |
|---------|-----|
| `step.prefix` | `step.step.prefix` |
| `step.text` | `step.step.text` |
| `step.status` | `step.result.status` |
| `step.ms` | `step.result.millis` |
| `step.logs` | `step.stepLog` |
| `step.nestedScenarios` | `step.callResults` |
| `feature.scenarios` | `feature.scenarioResults` |

### Phase 3: Cucumber JSON ✅

**Goal:** Generate per-feature Cucumber JSON files asynchronously as features complete.

**Status:** COMPLETE

**Implementation:**
- `CucumberJsonReportListener` implements `ResultListener`, uses async executor
- `CucumberJsonWriter.writeFeature()` converts `FeatureResult` to Cucumber JSON
- Output: `{packageQualifiedName}.json` per feature (e.g., `features.users.json`)
- Non-blocking: uses same pattern as `HtmlReportListener`

```
FeatureResult (on feature completion)
        ↓
CucumberJsonReportListener.onFeatureEnd()  (async executor)
        ↓
CucumberJsonWriter.writeFeature()
        ↓
{packageQualifiedName}.json
```

#### Conversion Mapping

| Karate JSON | Cucumber JSON |
|-------------|---------------|
| `name` | `name` |
| `relativePath` | `uri` |
| `scenarioResults` | `elements` (with background interleaved) |
| `stepResults` | `steps` |
| `step.prefix` | `keyword` |
| `step.text` | `name` |
| `result.nanos` | `result.duration` |
| `stepLog` | `doc_string.value` |
| `embeds` | `embeddings` |
| Nested `callResults` | Flatten with `>` prefix on keywords |

### Phase 4: JSONL Event Writer ✅

**Status:** COMPLETE

Implement `JsonLinesEventWriter` with standard envelope. Uses pattern matching on sealed `RunEvent` types:

```java
public class JsonLinesEventWriter implements RunListener {

    private final Path outputPath;
    private final BufferedWriter writer;

    @Override
    public boolean onEvent(RunEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", event.getType().name());
        envelope.put("ts", System.currentTimeMillis());
        envelope.put("threadId", getThreadId());  // nullable string
        envelope.put("data", event.toJson());  // Each event type serializes itself

        writeLine(Json.stringify(envelope));
        return true;
    }
}
```

Each sealed event type implements `toJson()`:

```java
// In SuiteRunEvent
public Map<String, Object> toJson() {
    return switch (type) {
        case SUITE_ENTER -> Map.of(
            "version", Globals.KARATE_VERSION,
            "schemaVersion", "1",
            "env", source.getEnv(),
            "threads", source.getThreadCount()
        );
        case SUITE_EXIT -> result.toKarateJson();
        default -> Map.of();
    };
}

// In FeatureRunEvent
public Map<String, Object> toJson() {
    return switch (type) {
        case FEATURE_ENTER -> Map.of(
            "path", source.getFeature().getResource().getRelativePath(),
            "name", source.getFeature().getName(),
            "line", source.getFeature().getLine()
        );
        case FEATURE_EXIT -> result.toKarateJson();
        default -> Map.of();
    };
}
```

### Phase 6: HTML Template Updates ✅

**Status:** COMPLETE (merged with Phase 2)

Update Alpine.js templates for new JSON structure:

**`karate-summary.html`:**
- Read from `SUITE_EXIT.data` in karate-events.jsonl (or inline JSON)
- Access `data.featureSummary` for feature table
- Access `data.featuresPassed`, `data.scenariosFailed`, etc. for stats

**`karate-feature.html`:**
- Read from `FEATURE_EXIT.data` or inline JSON
- Access `feature.scenarioResults` (not `feature.scenarios`)
- Access `step.step.prefix`, `step.step.text` (nested structure)
- Access `step.result.status`, `step.result.millis`
- Access `step.callResults` for nested calls

**Template field mapping:**
```javascript
// Current (toMap)              → New (toKarateJson)
step.prefix                     → step.step.prefix
step.text                       → step.step.text (trimmed)
step.status                     → step.result.status
step.ms                         → step.result.millis
step.logs                       → step.stepLog
step.nestedScenarios            → step.callResults
feature.scenarios               → feature.scenarioResults

// Keyword detection in JS
step.step.text.startsWith('def ')   // check for 'def' keyword
step.step.text.startsWith('url ')   // check for 'url' keyword
```

---

## V1 vs V2 Field Comparison

### FeatureResult Fields

| Field | V2 Current | V1 | Action |
|-------|------------|-----|--------|
| `name` | ✓ | ✓ | Keep |
| `description` | ✓ | ✓ | Keep |
| `durationMillis` | ✓ (in toMap) | ✓ | **Add to toKarateJson** |
| `passedCount` | ✓ (in toMap) | ✓ | **Add to toKarateJson** |
| `failedCount` | ✓ (in toMap) | ✓ | **Add to toKarateJson** |
| `packageQualifiedName` | ✗ | ✓ | **Add** |
| `relativePath` | ✓ (as `path`) | ✓ | **Rename to relativePath** |
| `resultDate` | ✗ | ✓ | **Add** |
| `prefixedPath` | ✗ | ✓ | **Add** |
| `scenarioResults` | ✗ (uses `elements`) | ✓ | **Rename from elements** |
| `callArg` | ✗ | ✓ | **Add** (for called features) |
| `loopIndex` | ✗ | ✓ | **Add** |
| `callDepth` | ✗ | ✓ | **Add** |
| `line` | ✓ | ✗ | Keep (useful) |
| `id` | ✓ | ✗ | Keep (useful) |
| `uri` | ✓ | ✗ | Remove (redundant with relativePath) |
| `tags` | ✓ | ✗ | Keep (useful for filtering) |
| `result` | ✓ | ✗ | Remove (v1 doesn't have summary object) |

### ScenarioResult Fields

| Field | V2 Current | V1 | Action |
|-------|------------|-----|--------|
| `name` | ✓ | ✓ | Keep |
| `description` | ✓ | ✓ | Keep |
| `line` | ✓ | ✓ | Keep |
| `durationMillis` | ✗ | ✓ | **Add** |
| `failed` | ✗ | ✓ | **Add** (boolean) |
| `refId` | ✗ | ✓ | **Add** |
| `error` | ✗ | ✓ | **Add** (error message string) |
| `sectionIndex` | ✗ | ✓ | **Add** |
| `exampleIndex` | ✗ | ✓ | **Add** |
| `exampleData` | ✗ | ✓ | **Add** (for outlines) |
| `executorName` | ✗ | ✓ | **Add** (thread name) |
| `startTime` | ✗ | ✓ | **Add** (epoch ms) |
| `endTime` | ✗ | ✓ | **Add** (epoch ms) |
| `stepResults` | ✗ (uses `steps`) | ✓ | **Rename from steps** |
| `id` | ✓ | ✗ | Keep |
| `tags` | ✓ (bug: uses getTags) | ✓ (uses getTagsEffective) | **Fix: use getTagsEffective()** |
| `result` | ✓ | ✗ | Remove (use individual fields) |

### StepResult Fields

**Use V1 nested structure:** `step: {...}` and `result: {...}` objects.

**step object:**

| Field | V1 | Action |
|-------|-----|--------|
| `step.background` | ✓ | Add (optional, only if true) |
| `step.index` | ✓ | Add |
| `step.line` | ✓ | Add |
| `step.endLine` | ✓ | Add (optional, if != line) |
| `step.comments` | ✓ | Add (optional) |
| `step.prefix` | ✓ | Add (`*`, `Given`, `When`, etc.) |
| `step.text` | ✓ | Add (text AFTER prefix, **trimmed**) |
| `step.docString` | ✓ | Add (optional) |
| `step.table` | ✓ | Add (optional) |

**result object:**

| Field | V1 | Action |
|-------|-----|--------|
| `result.status` | ✓ | Add (`passed`, `failed`, `skipped`) |
| `result.millis` | ✓ | Add (convenience) |
| `result.nanos` | ✓ | Add (actual duration) |
| `result.startTime` | ✓ | Add (epoch ms) |
| `result.endTime` | ✓ | Add (epoch ms) |
| `result.errorMessage` | ✓ | Add (optional, only if failed) |
| `result.aborted` | ✓ | Add (optional, only if true) |

**Top-level fields:**

| Field | V1 | Action |
|-------|-----|--------|
| `hidden` | ✓ | Add (optional, only if true) |
| `stepLog` | ✓ | Add (execution log/output) |
| `embeds` | ✓ | Add (array of embed objects) |
| `callResults` | ✓ | Add (array of FeatureResult for called features) |

---

## V1 Reference

**V1 codebase location:** `/Users/peter/dev/zcode/karate/karate-core/src/main/java/com/intuit/karate/`

Key files:
- `core/FeatureResult.java` - `toKarateJson()`, `toCucumberJson()`, `toSummaryJson()`
- `core/ScenarioResult.java` - `toKarateJson()`, `toCucumberJson()`
- `core/StepResult.java` - `toKarateJson()`
- `core/Step.java` - `toKarateJson()`
- `core/Result.java` - `toKarateJson()`
- `Results.java` - Suite-level `toKarateJson()` with summary stats

**V1 Cucumber JSON test file:** `/Users/peter/dev/zcode/karate/karate-core/src/test/java/com/intuit/karate/core/feature-result-cucumber.json`

This file defines the **final** cucumber JSON format that must not change.

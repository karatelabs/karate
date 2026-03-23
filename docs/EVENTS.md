# Karate v2 Event System

> See also: [REPORTS.md](./REPORTS.md) | [RUNTIME.md](./RUNTIME.md)

---

## Overview

The event system provides a unified way to observe and control test execution. It replaces the separate `RuntimeHook` and `ResultListener` interfaces with a single `RunListener` API.

```
Suite.fireEvent(RunEvent)
         ↓
   RunListener.onEvent(RunEvent)
         ↓
   return boolean (true = continue, false = skip for ENTER events)
```

---

## Event Types

```java
public enum RunEventType {
    SUITE_ENTER, SUITE_EXIT,
    FEATURE_ENTER, FEATURE_EXIT,
    SCENARIO_ENTER, SCENARIO_EXIT,
    STEP_ENTER, STEP_EXIT,
    HTTP_ENTER, HTTP_EXIT,
    ERROR, PROGRESS
}
```

### Event Lifecycle

```
SUITE_ENTER
├── FEATURE_ENTER
│   ├── SCENARIO_ENTER
│   │   ├── STEP_ENTER
│   │   │   ├── HTTP_ENTER → [http request] → HTTP_EXIT  (if step makes HTTP call)
│   │   │   └── ...
│   │   └── STEP_EXIT
│   └── SCENARIO_EXIT
└── FEATURE_EXIT
SUITE_EXIT
```

**Notes:**
- Events fire for **all** features including `call`ed ones - use `event.isTopLevel()` to filter
- `ERROR` fires on exceptions; `STEP_EXIT` still fires after with failure in `StepResult`
- Return `false` from `*_ENTER` events to skip execution
- `HTTP_ENTER`/`HTTP_EXIT` fire for each HTTP request, including retries

---

## Core Interfaces

### RunEvent

```java
public interface RunEvent {
    RunEventType getType();
    Map<String, Object> toJson();  // for JSONL serialization
}
```

Core event records: `SuiteRunEvent`, `FeatureRunEvent`, `ScenarioRunEvent`, `StepRunEvent`, `HttpRunEvent`, `ErrorRunEvent`, `ProgressRunEvent`

Each provides access to runtime objects (e.g., `ScenarioRuntime` for variable inspection, `FeatureRuntime` for call stack).

### RunListener

```java
public interface RunListener {
    default boolean onEvent(RunEvent event) { return true; }
}
```

### RunListenerFactory

For per-thread state (e.g., debuggers):

```java
@FunctionalInterface
public interface RunListenerFactory {
    RunListener create();  // called once per execution thread
}
```

---

## Usage

### Java API

```java
Suite.of("features/")
    .listener(event -> switch (event) {
        case ScenarioRunEvent e when e.type() == SCENARIO_ENTER -> {
            System.out.println("Starting: " + e.source().getScenario().getName());
            yield true;
        }
        case StepRunEvent e when e.type() == STEP_EXIT && e.result().isFailed() -> {
            System.out.println("Step failed: " + e.step().getText());
            yield true;
        }
        default -> true;
    })
    .parallel(10)
    .run();
```

### Skipping Scenarios

```java
Suite.of("features/")
    .listener(event -> switch (event) {
        case ScenarioRunEvent e when e.type() == SCENARIO_ENTER -> {
            yield !e.source().getScenario().getName().contains("slow");
        }
        default -> true;
    })
    .run();
```

### Per-Thread Listeners

```java
Suite.of("features/")
    .listenerFactory(() -> new MyDebugListener())
    .parallel(10)
    .run();
```

---

## HTTP Events

HTTP events enable extensions to capture request/response data for custom reports (e.g., OpenAPI coverage, request logging).

### HttpRunEvent

```java
public record HttpRunEvent(
    RunEventType type,           // HTTP_ENTER or HTTP_EXIT
    HttpRequest request,         // The HTTP request
    HttpResponse response,       // null for ENTER, populated for EXIT
    ScenarioRuntime scenarioRuntime,
    long timeStamp
) implements RunEvent
```

**Key methods:**
- `getCurrentStep()` - Returns the step that triggered this HTTP request (for correlation)
- `request().toMap()` - Serialize request to JSON (url, method, headers, body)
- `response().toMap()` - Serialize response to JSON (status, headers, body, responseTime)
- `response().isSkipped()` - Returns true if request was skipped by listener

### Capturing HTTP Data

```java
Suite.of("features/")
    .listener(event -> switch (event) {
        case HttpRunEvent e when e.type() == HTTP_EXIT -> {
            Step step = e.getCurrentStep();
            HttpRequest req = e.request();
            HttpResponse res = e.response();

            // Log or capture for reports
            System.out.printf("%s %s -> %d%n",
                req.getMethod(), req.getUrlAndPath(), res.getStatus());
            yield true;
        }
        default -> true;
    })
    .run();
```

### Skipping/Mocking HTTP Requests

Return `false` from `HTTP_ENTER` to skip the actual HTTP call. This enables commercial mocking tools:

```java
Suite.of("features/")
    .listener(event -> switch (event) {
        case HttpRunEvent e when e.type() == HTTP_ENTER -> {
            // Return false to skip this request
            yield !shouldMock(e.request());
        }
        case HttpRunEvent e when e.type() == HTTP_EXIT && e.response().isSkipped() -> {
            // Inject mock response into runtime variables
            e.scenarioRuntime().setVariable("response", mockBody);
            e.scenarioRuntime().setVariable("responseStatus", 200);
            yield true;
        }
        default -> true;
    })
    .run();
```

### Adding HTTP Data to Embeds

Use `LogContext` to add HTTP data as step embeds (for HTML reports):

```java
case HttpRunEvent e when e.type() == HTTP_EXIT -> {
    Map<String, Object> httpData = new LinkedHashMap<>();
    httpData.put("request", e.request().toMap());
    httpData.put("response", e.response().toMap());

    LogContext.get().embed(
        Json.stringify(httpData).getBytes(),
        "application/json",
        "http-exchange"
    );
    yield true;
}
```

---

## JSONL Event Stream

Karate writes events to `karate-events.jsonl` with a standard envelope:

```json
{"type":"FEATURE_EXIT","ts":1703500000200,"threadId":"worker-1","data":{...}}
```

**Note:** STEP and HTTP events are **not** written to JSONL (too granular). Commercial listeners can capture HTTP data and add to step embeds, which appear in `FEATURE_EXIT` results.

| Field | Description |
|-------|-------------|
| `type` | Event type (UPPER_CASE) |
| `ts` | Epoch milliseconds (UTC, timezone-agnostic) |
| `threadId` | Thread ID (nullable for suite-level) |
| `data` | Event payload - `FEATURE_EXIT` contains full `toKarateJson()` |

**Enable with:** `Suite.outputJsonLines(true)` or `--jsonl` CLI flag

See [REPORTS.md](./REPORTS.md#jsonl-event-stream) for complete format specification.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **ENTER/EXIT naming** | Matches `karate-js` pattern, conveys hierarchical scope semantics |
| **Single `onEvent()` method** | Pattern matching makes dispatch elegant; new event types don't require interface changes |
| **Non-sealed `RunEvent`** | Extensions can contribute custom events (HTTP, Mock, etc.) to central event bus |
| **Full runtime objects in events** | Debuggers need `ScenarioRuntime` for step control, variable inspection |
| **`threadId` as string** | Flexibility for future thread naming schemes |

---

## Future Event Types

| Type | Purpose |
|------|---------|
| `MOCK_REQUEST` | Mock server request handling |
| `CALL_ENTER` / `CALL_EXIT` | Feature call tracking (opt-in) |
| `RETRY` | Scenario retry attempts |

---

## Source Files

| File | Description |
|------|-------------|
| `RunEventType.java` | Event type enum |
| `RunEvent.java` | Event interface |
| `HttpRunEvent.java` | HTTP request/response event record |
| `StepRunEvent.java` | Step enter/exit event record |
| `RunListener.java` | Listener interface |
| `RunListenerFactory.java` | Per-thread listener factory |
| `JsonLinesEventWriter.java` | JSONL file writer |

# Karate v2 Runtime Design

> See also: [CLI.md](./CLI.md) | [PARSER.md](./PARSER.md) | [JS_ENGINE.md](./JS_ENGINE.md) | [REPORTS.md](./REPORTS.md) | [EVENTS.md](./EVENTS.md) | [PRINCIPLES.md](./PRINCIPLES.md) | [GATLING.md](./GATLING.md)

---

## Architecture Overview

```
Suite → FeatureRuntime → ScenarioRuntime → StepExecutor
                                               ↓
                              ┌────────────────┼────────────────┐
                              ▼                ▼                ▼
                         Match Engine    Http Client    Other Actions
```

**V1 Reference:** `/Users/peter/dev/zcode/karate/karate-core/src/main/java/com/intuit/karate/`

---

## Core Classes

| Class | Description |
|-------|-------------|
| `Suite` | Top-level orchestrator, config loading, parallel execution |
| `FeatureRuntime` | Feature execution, scenario iteration, callOnce caching |
| `ScenarioRuntime` | Scenario execution, variable scope |
| `StepExecutor` | Keyword-based step dispatch |
| `KarateJs` | JS engine bridge, `karate.*` methods |
| `KarateJsBase` | Shared state and infrastructure for KarateJs |
| `KarateJsUtils` | Stateless utility methods for `karate.*` API |
| `KarateJsContext` | Runtime context interface (implemented by ScenarioRuntime) |
| `HttpClientFactory` | Factory for creating HttpClient instances (extensibility for Gatling) |
| `DefaultHttpClientFactory` | Default factory creating per-instance ApacheHttpClient |
| `CommandProvider` | SPI for CLI subcommand registration (see [CLI.md](./CLI.md)) |
| `Runner` | Fluent API for test execution |
| `RunListener` | Unified event listener for test execution (see [EVENTS.md](./EVENTS.md)) |
| `RunListenerFactory` | Per-thread listener factory for debuggers |
| `ResultListener` | Observation interface for reporting |

**Reports (io.karatelabs.output):** `HtmlReportListener`, `JunitXmlWriter`, `CucumberJsonWriter`, `JsonLinesReportListener`

**Results:** `StepResult`, `ScenarioResult`, `FeatureResult`, `SuiteResult`

---

## Implemented Features

### Step Keywords
- **Variables:** `def`, `set`, `remove`, `text`, `json`, `xml`, `csv`, `yaml`, `string`, `xmlstring`, `copy`, `table`, `replace`
- **Assertions:** `match` (all operators), `assert`, `print`
- **HTTP:** `url`, `path`, `param`, `params`, `header`, `headers`, `cookie`, `cookies`, `form field`, `form fields`, `request`, `retry until`, `method`, `status`, `multipart file/field/fields/files/entity`
- **Control:** `call`, `callonce`, `eval`, `doc`
- **Config:** `configure` (see [Configure Keys](#configure-keys))

### Built-in Tags
| Tag | Description |
|-----|-------------|
| `@ignore` | Skip execution (feature or scenario level) |
| `@env=<name>` | Run only when `karate.env` matches |
| `@envnot=<name>` | Skip when `karate.env` matches |
| `@setup` | Data provider for dynamic outlines |
| `@fail` | Expect failure (invert result) |

### Tag Inheritance

Feature-level tags are inherited by all scenarios in that feature. When checking tags, the effective tags are the **merge of feature + scenario tags**.

```gherkin
@api @ignore
Feature: Helper for tests
  # All scenarios inherit @api and @ignore

Scenario: Setup data
  # Effective tags: @api, @ignore
  * def data = { }
```

### @ignore Behavior

The `@ignore` tag skips scenarios/features from execution:

- **Feature-level `@ignore`**: The entire feature is skipped by the runner (does not appear in reports)
- **Scenario-level `@ignore`**: That specific scenario is skipped
- **Called features**: `@ignore` does NOT prevent a feature from being called via `call read('...')`

This allows creating helper features that are only executed when explicitly called:

```gherkin
@ignore
Feature: Helper utilities
  # Not executed by runner, but can be called

Scenario: Generate test data
  * def result = { id: 1 }
```

```gherkin
Feature: Main tests

Scenario: Use helper
  * def data = call read('helper.feature')  # Works despite @ignore
  * match data.result.id == 1
```

### Runner API
```java
SuiteResult result = Runner.path("src/test/resources")
    .tags("@smoke", "~@slow")
    .karateEnv("dev")
    .outputJunitXml(true)
    .outputCucumberJson(true)
    .parallel(5);
```

### Line Number Filtering

Scenarios can be selected by line number using the `path:line` syntax:

```java
// Run specific scenario at line 10
Runner.path("features/users.feature:10").parallel(1);

// Run multiple scenarios (lines 10 and 25)
Runner.path("features/users.feature:10:25").parallel(1);
```

**Implementation:** Line numbers are parsed in `Runner.Builder.resolveFeatures()` and stored in a `Map<String, Set<Integer>>` keyed by feature URI. The `FeatureRuntime.ScenarioIterator.shouldSelect()` method checks line filters before tag filters.

**Matching logic (in `matchesLineFilter()`):**
1. Direct match on scenario declaration line
2. For Scenario Outlines: also matches the outline declaration line (runs all examples)
3. Match any line within the scenario's step range (between declaration and last step)

**Precedence:** Line number selection **bypasses all tag filters** (`@ignore`, `@env`, tag expressions). This allows running specific scenarios regardless of tags, which is essential for IDE integrations and debugging.

### Caching: callOnce vs callSingle

| Method | Scope | Use Case |
|--------|-------|----------|
| `callonce` | Feature-scoped | Setup shared within a feature (e.g., test data for scenarios) |
| `karate.callSingle()` | Suite-scoped | Global setup (e.g., auth token for entire test run) |

**callOnce Behavior:**
- Executes the called feature **once per feature file**
- Uses feature-level cache with `ReentrantLock` for thread safety
- Blocks scenarios within the **same feature** until cached
- Does **NOT** block scenarios in **other features** running in parallel

```gherkin
Feature: User tests

Background:
  * callonce read('setup-user.feature')  # Runs once for this feature

Scenario: Test 1
  # Uses cached result from callonce

Scenario: Test 2
  # Uses same cached result
```

**callSingle Behavior:**
- Executes **once globally** for entire test suite
- Uses suite-level cache (shared across all features)
- Ideal for `karate-config.js` initialization
- **Disk caching:** Configure `callSingleCache` to persist results across test runs
  - Default cache location: `<buildDir>/karate-temp/cache/`
  - Cleaned by `karate clean`

```javascript
// karate-config.js
function fn() {
  var token = karate.callSingle('classpath:auth/get-token.feature');
  return { authToken: token.accessToken };
}
```

```gherkin
# Enable disk caching for 15 minutes
* configure callSingleCache = { minutes: 15 }

# Custom cache directory
* configure callSingleCache = { minutes: 15, dir: 'custom/cache' }
```

### HttpClientFactory

The `HttpClientFactory` interface allows custom HTTP client creation for extensibility:

```java
public interface HttpClientFactory {
    HttpClient create();
}
```

**DefaultHttpClientFactory:** Creates a new `ApacheHttpClient` per instance (standard for functional tests with isolation).

**Custom factories** can provide pooled connections for performance testing. See [GATLING.md](./GATLING.md) for the `PooledHttpClientFactory` used in karate-gatling.

```java
// Custom factory usage
KarateJs karate = new KarateJs(root, customFactory);
```

### System Properties
| Property | Description |
|----------|-------------|
| `karate.env` | Environment name |
| `karate.config.dir` | Config directory |
| `karate.output.dir` | Build directory override (bypasses Maven/Gradle detection) |
| `karate.working.dir` | Working directory |

**Build directory detection:** Karate auto-detects `target/` (Maven) or `build/` (Gradle) based on project files. Override with `-Dkarate.output.dir=custom`.

---

## V1 Differences

Intentional deviations from V1 behavior:

| Feature | V1 Behavior | V2 Behavior | Rationale |
|---------|-------------|-------------|-----------|
| Variable names | Must start with letter (`[a-zA-Z]`) | Can start with letter or underscore (`[a-zA-Z_]`) | More permissive, aligns with JavaScript conventions |

---

## karate.* API

### Implemented

| Method | Location | Description |
|--------|----------|-------------|
| `abort()` | KarateJs | Abort scenario execution |
| `append(list, items...)` | KarateJsUtils | Append items to list (returns new list) |
| `appendTo(list, items...)` | KarateJsUtils | Append items to list (mutates) |
| `call(path, arg?)` | KarateJs | Call feature file |
| `callonce(path, arg?)` | KarateJs | Call feature once per feature |
| `callSingle(path, arg?)` | KarateJs | Call once per suite (cached) |
| `config` | KarateJs | Get config object |
| `configure(key, value)` | KarateJs | Set configuration |
| `distinct(list)` | KarateJsUtils | Remove duplicates |
| `doc(template)` | KarateJs | Render HTML template |
| `embed(data, [mimeType], [name])` | KarateJs | Embed content in report (auto-detects type) |
| `env` | KarateJs | Get karate.env value |
| `eval(expression)` | KarateJs | Evaluate JS expression |
| `feature` | KarateJs | Get feature info (name, description, prefixedPath, fileName, parentDir) |
| `exec(command)` | KarateJs | Execute system command |
| `extract(text, regex, group)` | KarateJsUtils | Extract regex match |
| `extractAll(text, regex, group)` | KarateJsUtils | Extract all regex matches |
| `fail(message)` | KarateJsUtils | Fail scenario with message |
| `filter(list, fn)` | KarateJsUtils | Filter list by predicate |
| `filterKeys(map, keys)` | KarateJsUtils | Filter map by keys |
| `forEach(collection, fn)` | KarateJsUtils | Iterate collection |
| `fork(options)` | KarateJs | Fork background process |
| `fromJson(string)` | KarateJsUtils | Parse JSON string |
| `fromString(text)` | KarateJs | Parse as JSON/XML or return string |
| `get(name, path?)` | KarateJs | Get variable value |
| `http(url)` | KarateJs | Create HTTP client |
| `info` | KarateJs | Get scenario info |
| `jsonPath(obj, path)` | KarateJsUtils | Apply JSONPath expression |
| `keysOf(map)` | KarateJsUtils | Get map keys |
| `log(args...)` | KarateJs | Log message |
| `lowerCase(value)` | KarateJsUtils | Lowercase string/JSON/XML |
| `map(list, fn)` | KarateJsUtils | Transform list |
| `mapWithKey(list, key)` | KarateJsUtils | Wrap list items in maps |
| `match(actual, expected)` | KarateJs | Match assertion |
| `merge(maps...)` | KarateJsUtils | Merge maps |
| `os` | KarateJs | Get OS info |
| `pause(ms)` | KarateJsUtils | Sleep for milliseconds |
| `pretty(value)` | KarateJsUtils | Format as pretty JSON/XML |
| `prettyXml(value)` | KarateJsUtils | Format as pretty XML |
| `proceed()` | KarateJs | Proceed in mock (passthrough) |
| `properties` | KarateJs | Get system properties |
| `range(start, end, step?)` | KarateJsUtils | Generate number range |
| `read(path)` | KarateJs | Read file content |
| `readAsBytes(path)` | KarateJs | Read file as bytes |
| `readAsString(path)` | KarateJs | Read file as string |
| `remove(name, path)` | KarateJs | Remove from variable |
| `repeat(count, fn)` | KarateJsUtils | Generate list by repeating function |
| `scenario` | KarateJs | Get scenario info (name, description, line, sectionIndex, exampleIndex, exampleData) |
| `scenarioOutline` | KarateJs | Get scenario outline info (null if not in outline) |
| `set(name, path?, value)` | KarateJs | Set variable value |
| `setup()` | KarateJs | Get setup scenario result |
| `setupOnce()` | KarateJs | Get cached setup result |
| `setXml(name, path, value)` | KarateJs | Set XML value |
| `signal(value)` | KarateJs | Signal to listener |
| `sizeOf(value)` | KarateJsUtils | Get size of list/map/string |
| `sort(list, fn)` | KarateJsUtils | Sort list by key function |
| `start(options)` | KarateJs | Start mock server |
| `tags` | KarateJs | Get effective tags list (feature + scenario) |
| `tagValues` | KarateJs | Get tag values map (tag name → list of values) |
| `toBean(obj, className)` | KarateJsUtils | Convert to Java bean |
| `toBytes(list)` | KarateJsUtils | Convert number list to byte[] |
| `toCsv(list)` | KarateJsUtils | Convert list of maps to CSV |
| `toJava(value)` | KarateJsUtils | No-op (V1 compat) |
| `toJson(value, removeNulls?)` | KarateJsUtils | Convert to JSON |
| `toString(value)` | KarateJsUtils | Convert to string (JSON/XML aware) |
| `typeOf(value)` | KarateJsUtils | Get Karate type name |
| `urlDecode(string)` | KarateJsUtils | URL decode |
| `urlEncode(string)` | KarateJsUtils | URL encode |
| `uuid()` | KarateJsUtils | Generate random UUID string |
| `valuesOf(map)` | KarateJsUtils | Get map values |
| `xmlPath(xml, path)` | KarateJsUtils | Apply XPath expression |
| `logger` | KarateJs | Log facade (debug/info/warn/error via LogContext) |
| `prevRequest` | KarateJs | Get previous HTTP request (method, url, headers, body) |
| `request` | KarateJs | Get current request body (mock context only) |
| `response` | KarateJs | Get current response (mock context only) |
| `readAsStream(path)` | KarateJs | Read file as InputStream |
| `render(template)` | KarateJs | Render HTML template (returns string) |
| `stop(port)` | KarateJsUtils | Debug breakpoint - pauses until connection received |
| `toAbsolutePath(path)` | KarateJs | Convert relative path to absolute |
| `waitForHttp(url, options)` | KarateJsUtils | Poll HTTP endpoint until available |
| `waitForPort(host, port)` | KarateJsUtils | Wait for TCP port to become available |
| `write(value, path)` | KarateJs | Write content to file in output directory |
| `expect(value)` | KarateJs | Chai-style BDD assertion API (see [karate.expect()](#karateexpect-chai-style-assertions)) |
| `faker` | KarateJs | Random test data generator (see [karate.faker.*](#karatefaker-api)) |

### Out of Scope (UI/Extensions)

| Method | Reason |
|--------|--------|
| `robot` | Desktop automation deferred (karate-robot) |
| `channel()`, `consume()` | Kafka extension |
| `webSocket()`, `webSocketBinary()` | WebSocket not yet |
| `compareImage()` | UI testing |

### Planned (Driver/Browser)

| Method | Phase | Notes |
|--------|-------|-------|
| `driver` | Phase 9 | Browser automation via `karate.driver()` - see [DRIVER.md](./DRIVER.md) |

### Performance Testing Integration

See [GATLING.md](./GATLING.md) for the karate-gatling module plan, including `PerfContext` interface for custom performance event capture.

### Pending V1 Parity (Advanced)

| Feature | V1 Pattern | Notes |
|---------|-----------|-------|
| Java Function as callable | `Hello.sayHelloFactory()` returns `Function<String,String>` | JS engine needs to wrap `java.util.function.Function`, `Callable`, `Runnable`, `Predicate` as `JsCallable` |
| callSingle returning Java fn | `karate.callSingle('file.js')` where JS returns Java Function | Depends on above |
| Tagged Examples in call/callSingle | `call 'file.feature@tag'` with `@tag` on Examples section | Tag on Examples should filter which outline rows to execute. V1: `call-single-tag-called.feature` |

**RESOLVED:** The `JsCallable` vs `JsFunction` issue has been fixed by changing `JsObject` and `JsArray` to implement `Invokable` instead of `JsCallable`. Now only actual callable functions (`JsFunction`, `JsBoolean`, `JsDate`, `JsNumber`, `JsString`, and Java interop callables) implement `JsCallable`, making `instanceof JsCallable` checks correct for identifying functions.

---

## karate.expect() Chai-Style Assertions

Chai-style BDD assertion API for more expressive test assertions.

```gherkin
# Basic assertions
* karate.expect(response.status).to.equal(200)
* karate.expect(response.name).to.be.a('string')
* karate.expect(response.items).to.have.length(3)

# Negation
* karate.expect(response.error).to.not.exist
* karate.expect(response.count).to.not.equal(0)

# Type checks
* karate.expect(response.active).to.be.true
* karate.expect(response.data).to.be.null
* karate.expect(response.list).to.be.a('array')

# Object assertions
* karate.expect(response).to.have.property('id')
* karate.expect(response).to.have.property('name', 'John')
* karate.expect(response).to.have.keys(['id', 'name', 'email'])
* karate.expect(response).to.have.all.keys(['id', 'name'])
* karate.expect(response).to.have.any.keys(['id', 'foo'])
* karate.expect(response).to.have.nested.property('user.address.city', 'NYC')

# Array assertions
* karate.expect(response.items).to.include({ id: 1 })
* karate.expect(response.tags).to.contain('api')
* karate.expect(response.items).to.deep.include({ user: { name: 'John' } })

# Numeric comparisons
* karate.expect(response.count).to.be.above(5)
* karate.expect(response.count).to.be.below(100)
* karate.expect(response.count).to.be.within(1, 10)
* karate.expect(response.count).to.be.at.least(1)
* karate.expect(response.count).to.be.at.most(100)
* karate.expect(response.price).to.be.closeTo(9.99, 0.01)

# String regex matching
* karate.expect(response.email).to.match(/^[\w.-]+@[\w.-]+\.\w+$/)

# Membership
* karate.expect(response.status).to.be.oneOf(['active', 'pending', 'completed'])

# Truthy/empty checks
* karate.expect(response.data).to.be.ok
* karate.expect(response.list).to.be.empty
```

### Assertion Chains

Language chains (`.to`, `.be`, `.that`, `.and`, `.which`) are available for readability:

```gherkin
* karate.expect(response).to.be.a('object').and.have.property('id')
* karate.expect(response.items).to.have.length(3).and.include({ id: 1 })
```

---

## karate.faker.* API

Random test data generators for API testing. Accessible via `karate.faker.*`.

```gherkin
# Names
* def firstName = karate.faker.firstName()    # e.g., "Ethan"
* def lastName = karate.faker.lastName()      # e.g., "Smith"
* def fullName = karate.faker.fullName()      # e.g., "Sophia Johnson"

# Contact
* def email = karate.faker.email()            # e.g., "ethan42@gmail.com"
* def userName = karate.faker.userName()      # e.g., "sophia.johnson"
* def phone = karate.faker.phoneNumber()      # e.g., "+1-555-123-4567"

# Location
* def city = karate.faker.city()              # e.g., "San Francisco"
* def country = karate.faker.country()        # e.g., "United States"
* def address = karate.faker.streetAddress()  # e.g., "1234 Oak Avenue"
* def zip = karate.faker.zipCode()            # e.g., "94105"
* def lat = karate.faker.latitude()           # e.g., 37.7749
* def lng = karate.faker.longitude()          # e.g., -122.4194

# Numbers
* def num = karate.faker.randomInt()          # 0-1000
* def num = karate.faker.randomInt(100)       # 0-100
* def num = karate.faker.randomInt(18, 65)    # 18-65
* def flt = karate.faker.randomFloat()        # 0.0-1.0
* def flt = karate.faker.randomFloat(10, 20)  # 10.0-20.0
* def bool = karate.faker.randomBoolean()     # true or false

# Text
* def word = karate.faker.word()              # e.g., "lorem"
* def sentence = karate.faker.sentence()      # e.g., "Lorem ipsum dolor sit amet."
* def paragraph = karate.faker.paragraph()    # Multiple sentences
* def code = karate.faker.alphanumeric(10)    # e.g., "aB3dE5fG7h"
* def color = karate.faker.hexColor()         # e.g., "#a3f2c1"

# Business
* def company = karate.faker.companyName()    # e.g., "Smith Technologies"
* def job = karate.faker.jobTitle()           # e.g., "Software Engineer"
* def cc = karate.faker.creditCardNumber()    # e.g., "4123456789012345" (fake)

# Timestamps
* def ts = karate.faker.timestamp()           # Unix timestamp (seconds)
* def tsMs = karate.faker.timestampMs()       # Unix timestamp (milliseconds)
* def iso = karate.faker.isoTimestamp()       # ISO 8601 format
```

---

## Configure Keys

### Implemented
`ssl`, `proxy`, `readTimeout`, `connectTimeout`, `followRedirects`, `headers`, `cookies`, `charset`, `retry`, `report`, `ntlmAuth`, `callSingleCache`, `continueOnStepFailure`, `httpRetryEnabled`, `url`, `localAddress`, `auth`

### configure auth

Type-discriminated authentication configuration for HTTP requests:

```gherkin
# Basic auth
* configure auth = { type: 'basic', username: 'user', password: 'pass' }

# Bearer token
* configure auth = { type: 'bearer', token: '#(accessToken)' }

# OAuth2 client_credentials
* configure auth = { type: 'oauth2', grantType: 'client_credentials', tokenUrl: 'https://auth.example.com/token', clientId: 'id', clientSecret: 'secret' }

# NTLM (requires HTTP client rebuild)
* configure auth = { type: 'ntlm', username: 'user', password: 'pass', domain: 'DOMAIN' }

# Disable auth
* configure auth = null
```

Embedded expressions are supported in auth config values (e.g., `token: '#(myVar)'`).

### TODO
| Key | Priority |
|-----|----------|
| `driver` | Phase 9 - see [DRIVER.md](./DRIVER.md) |

### Removed
`printEnabled`, `logPrettyRequest`, `logPrettyResponse`, `lowerCaseResponseHeaders`

### Out of Scope
`robot`, `driverTarget`, `kafka`, `grpc`, `websocket`, `webhook`, `responseHeaders`, `responseDelay`, `cors`

---

## Not Yet Implemented

### Priority 7: JavaScript Script Execution (`*.karate.js`)

Pure JavaScript files with the `*.karate.js` naming convention can be executed directly:

```javascript
// server-test.karate.js
var proc = karate.fork({
  args: ['node', 'server.js'],
  listener: function(line) {
    if (line.contains('listening')) {
      karate.signal({ ready: true })
    }
  }
})

var result = karate.listen(5000)
if (!result.ready) throw 'Server did not start'

var http = karate.http('http://localhost:8080')
var response = http.path('health').get()
match(response.body).equals({ status: 'ok' })

proc.close()
```

**Implementation:** `JsScriptRuntime` - executes `.karate.js` with KarateJs context, results mapped to `ScenarioResult`.

---

### Priority 9: Configure Report

```cucumber
* configure report = { showJsLineNumbers: true }
```

Controls report verbosity, JS line-level capture, HTTP detail, payload size limits.

See [REPORTS.md](./REPORTS.md) for specification.

---

### Priority 9: karate-base.js

Shared config from classpath (e.g., company JAR):

```
karate-base.js (from JAR)
  ↓ overridden by
karate-config.js (project)
  ↓ overridden by
karate-config-dev.js (env-specific)
```

---

## Future Phase

| Feature | Description |
|---------|-------------|
| `@lock=<name>` | Mutual exclusion - scenarios with same lock run sequentially. `@lock=*` runs exclusively. |
| `@retry` | Re-run failed scenarios. CLI: `karate --rerun target/karate-reports/rerun.txt` |
| Multiple Suite Execution | `Runner.suites().add(...).parallel(n).run()` for grouped execution |
| Telemetry | Anonymous usage ping. Opt-out: `KARATE_TELEMETRY=false` |

---

### Plugin Architecture

| Interface | Purpose | Discovery | Status |
|-----------|---------|-----------|--------|
| `CommandProvider` | CLI subcommands | ServiceLoader | Implemented |
| `HttpClientFactory` | Custom HTTP clients | Constructor injection | ✅ Implemented |
| `RunListener` | Event listeners | `Runner.listener()` or `--listener` CLI | ✅ Implemented |
| `RunListenerFactory` | Per-thread listeners | `Runner.listenerFactory()` or `--listener-factory` CLI | ✅ Implemented |
| `ReportWriterFactory` | Custom report formats | ServiceLoader | Planned |

**ServiceLoader:** User drops JAR in `~/.karate/ext/`, plugin is automatically available via `META-INF/services/`.

**Constructor injection:** Pass factory to `KarateJs` constructor (see HttpClientFactory).

---

### Root Bindings

Built-in variables (`karate`, `read`, `match`, `__arg`, `__row`, `__num`) are stored as "root bindings" via `Engine.putRootBinding()`. These are accessible during JS evaluation but excluded from `getAllVariables()`, so only user-defined variables appear in reports and variable dumps.

---

## Test Classes

| Class | Coverage |
|-------|----------|
| `StepDefTest` | def, set, copy, table, replace, csv, yaml |
| `StepMatchTest` | match assertions |
| `StepHttpTest` | HTTP keywords |
| `StepMultipartTest` | multipart |
| `StepJsTest` | JS functions, karate.* API |
| `StepXmlTest` | XML operations |
| `StepCallTest` | call/callonce |
| `StepAbortTest` | karate.abort() |
| `StepEvalTest` | eval keyword |
| `StepInfoTest` | karate.info, scenario, feature, tags, tagValues, scenarioOutline |
| `OutlineTest` | Scenario outline, dynamic |
| `CallSingleTest` | karate.callSingle() |
| `DataUtilsTest` | CSV/YAML parsing |
| `TagSelectorTest` | Tag selectors: anyOf, allOf, not, valuesFor, @env |
| `StepExpectTest` | karate.expect() chai-style assertions |
| `StepFakerTest` | karate.faker.* random data generators |

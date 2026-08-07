# Karate-Gatling v2 Port Plan

This document describes the plan to port karate-gatling from v1 to karate.

## Summary of Decisions

| Decision | Choice |
|----------|--------|
| Gatling version | 3.13.x (latest stable) |
| Scala version | 3.x only (no Scala DSL layer needed) |
| Integration approach | v2 PerfHook + RunListener event system |
| DSL strategy | Java-only DSL (Scala users use Java DSL directly) |
| Async runtime | Match Gatling's execution model with PerfHook.submit() |
| Session variables | `__karate`/`__gatling` prefix only — **breaking vs v1** (no unprefixed top-level access) |
| Module location | Separate `karate-gatling` module |
| Scope | V1 parity first, then profiling validation |
| Failure handling | Abort immediately on first failure, report partial results |
| HTTP pooling | HttpClientFactory for Gatling pooled connections |
| Request timing | Include connection pool wait time |
| callOnce caching | Feature-scoped (fix race condition in karate-core) |
| Request names | User nameResolver only (no auto GraphQL detection) |
| Silent mode | Suppress ALL (Gatling metrics + Karate HTML/logs) |
| Custom events | capturePerfEvent() for HTTP + non-HTTP (DB, gRPC) |
| Report formats | Both HTML (Highcharts) and JSON (--format json) |
| CLI scope | Features only (no --simulation class support) |
| Pause API | Keep karate.pause() for Gatling integration |
| Timeout | Abort mid-request via HttpClient.abort() |
| Feature parsing | Fresh parse per scenario (no caching) |
| Gatling edition | OSS only |

---

## 1. Module Structure

Create new Maven module: `karate-gatling`

```
karate/
├── karate-js/
├── karate-core/
├── karate-gatling/          # NEW
│   ├── pom.xml
│   ├── src/main/java/io/karatelabs/gatling/
│   │   ├── KarateDsl.java           # Public Java DSL entry point
│   │   ├── KarateProtocol.java      # Gatling protocol implementation
│   │   ├── KarateProtocolBuilder.java
│   │   ├── KarateFeatureAction.java # Feature execution action
│   │   ├── KarateFeatureBuilder.java
│   │   ├── KarateSetAction.java     # Session variable injection
│   │   ├── KarateUriPattern.java    # URI pattern + pause config
│   │   └── MethodPause.java         # Method/pause data class
│   ├── src/test/java/
│   │   └── io/karatelabs/gatling/
│   │       ├── GatlingSimulation.java  # Comprehensive test simulation
│   │       └── MockServer.java         # Test mock using v2 native mocks
│   ├── src/test/resources/
│   │   ├── karate-config.js
│   │   ├── features/                # Test feature files
│   │   └── logback-test.xml
│   └── README.md                    # Ported from v1 with updates
└── pom.xml                          # Add karate-gatling to modules
```

---

## 2. Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Karate -->
    <dependency>
        <groupId>io.karatelabs</groupId>
        <artifactId>karate-core</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- Gatling 3.13.x -->
    <dependency>
        <groupId>io.gatling</groupId>
        <artifactId>gatling-core-java</artifactId>
        <version>3.13.5</version>
    </dependency>
    <dependency>
        <groupId>io.gatling.highcharts</groupId>
        <artifactId>gatling-charts-highcharts</artifactId>
        <version>3.13.5</version>
    </dependency>

    <!-- Scala 3 (for Gatling compatibility) -->
    <dependency>
        <groupId>org.scala-lang</groupId>
        <artifactId>scala3-library_3</artifactId>
        <version>3.4.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Scala 3 compiler -->
        <plugin>
            <groupId>net.alchim31.maven</groupId>
            <artifactId>scala-maven-plugin</artifactId>
            <version>4.9.0</version>
            <configuration>
                <scalaVersion>3.4.0</scalaVersion>
            </configuration>
        </plugin>

        <!-- Gatling Maven plugin -->
        <plugin>
            <groupId>io.gatling</groupId>
            <artifactId>gatling-maven-plugin</artifactId>
            <version>4.20.16</version>
        </plugin>
    </plugins>
</build>
```

---

## 2.1 HTTP Client Factory ✅ DONE

**Implemented in karate-core:** `HttpClientFactory` interface and `DefaultHttpClientFactory`.

`KarateJs` constructor accepts optional `HttpClientFactory`. The `PooledHttpClientFactory` for Gatling connection pooling will be implemented in the karate-gatling module.

See [DESIGN.md](./DESIGN.md) for details.

---

## 2.2 Caching Fixes ✅ DONE

**callOnce** now uses feature-scoped caching with `ReentrantLock` for thread safety (commit 15ad313).

- `callOnce` blocks scenarios within the **same feature** only
- `callOnce` does **NOT** block scenarios in **other features** running in parallel
- `karate.callSingle()` remains suite-scoped (once globally per test run)

See [DESIGN.md](./DESIGN.md) for details.

---

## 3. Core Classes Implementation

### 3.1 KarateDsl.java (Public API)

```java
package io.karatelabs.gatling;

public final class KarateDsl {

    // URI pattern builder
    public static KarateUriPattern.Builder uri(String pattern) { ... }

    // Protocol builder
    public static KarateProtocolBuilder karateProtocol(KarateUriPattern... patterns) { ... }

    // Feature action builder
    public static KarateFeatureBuilder karateFeature(String path, String... tags) { ... }

    // Session variable injection
    public static ActionBuilder karateSet(String key, Function<Session, Object> supplier) { ... }

    // Method pause helper
    public static MethodPause method(String method, int pauseMillis) { ... }
}
```

### 3.2 KarateProtocol.java

Key changes from v1:
- Use v2's `io.karatelabs.http.HttpRequest` and `io.karatelabs.core.ScenarioRuntime`
- Leverage v2's `Suite.getCallSingleCache()` and `Suite.getCallOnceCache()` (already ConcurrentHashMap)
- Expose `Runner.Builder` directly via `protocol.runner()`

```java
package io.karatelabs.gatling;

public class KarateProtocol implements Protocol {

    public static final String KARATE_KEY = "__karate";
    public static final String GATLING_KEY = "__gatling";

    private final Map<String, List<MethodPause>> uriPatterns;
    private BiFunction<HttpRequest, ScenarioRuntime, String> nameResolver = (req, sr) -> null;
    private Runner.Builder runner = Runner.builder();

    // Default name resolver using URI pattern matching
    public String defaultNameResolver(HttpRequest req, ScenarioRuntime sr) {
        String path = extractPath(req.getUrl());
        return uriPatterns.keySet().stream()
            .filter(pattern -> pathMatches(pattern, path))
            .findFirst()
            .orElse(path);
    }

    // Pause lookup
    public int pauseFor(String requestName, String method) { ... }

    // URI pattern matching (port from v1)
    public boolean pathMatches(String pattern, String path) { ... }
}
```

### 3.3 KarateFeatureAction.java

Key changes from v1:
- Use v2's PerfHook interface (maintains `submit()` abstraction from v1)
- Match Gatling's execution model (not direct virtual threads)
- Integrate with v2's `Suite`, `FeatureRuntime`, `ScenarioRuntime`
- **Abort immediately on first failure**, report partial results
- **Abort mid-request on Gatling timeout** via `HttpClient.abort()`

```java
package io.karatelabs.gatling;

public class KarateFeatureAction implements Action {

    private final String featurePath;
    private final String[] tags;
    private final KarateProtocol protocol;
    private final StatsEngine statsEngine;
    private final Action next;
    private final boolean silent;

    @Override
    public void execute(Session session) {
        // Use PerfHook.submit() to match Gatling's execution model
        protocol.getPerfHook().submit(() -> executeFeature(session));
    }

    private void executeFeature(Session session) {
        // 1. Prepare session maps
        Map<String, Object> gatlingVars = new HashMap<>(session.attributes());
        Map<String, Object> karateVars = getOrCreate(session, KARATE_KEY);
        karateVars.put(GATLING_KEY, gatlingVars);

        // 2. Create Suite with PerfHook for metrics
        Suite suite = Suite.builder()
            .path(featurePath)
            .tags(tags)
            .perfHook(createPerfHook(session))
            .httpClientFactory(protocol.getHttpClientFactory())
            .build();

        // 3. Execute - aborts immediately on first failure
        SuiteResult result = suite.run();

        // 4. Update session and continue
        Session updated = updateSession(session, result);
        next.execute(updated);
    }

    private PerfHook createPerfHook(Session session) {
        return new PerfHook() {
            private HttpClient currentClient;

            @Override
            public String getPerfEventName(HttpRequest req, ScenarioRuntime sr) {
                String customName = protocol.getNameResolver().apply(req, sr);
                if (customName != null) return customName;
                return protocol.defaultNameResolver(req, sr);
            }

            @Override
            public void reportPerfEvent(PerfEvent event) {
                if (silent) return; // Skip for warm-up

                Status status = event.isFailed() ? KO : OK;
                statsEngine.logResponse(
                    session.scenario(),
                    session.groups(),
                    event.getName(),
                    event.getStartTime(),
                    event.getEndTime(),
                    status,
                    Option.apply(String.valueOf(event.getStatusCode())),
                    event.getMessage() != null ? Option.apply(event.getMessage()) : Option.empty()
                );

                // Apply pause after request
                int pauseMs = protocol.pauseFor(event.getName(), event.getMethod());
                if (pauseMs > 0) {
                    pause(pauseMs);
                }
            }

            @Override
            public void submit(Runnable runnable) {
                // Matches Gatling's execution model
                runnable.run();
            }

            @Override
            public void pause(Number millis) {
                // Use Gatling's non-blocking pause mechanism
                try {
                    Await.result(Future.never(), Duration.apply(millis.longValue(), TimeUnit.MILLISECONDS));
                } catch (TimeoutException e) {
                    // Expected - this is how Gatling's pause works
                }
            }

            @Override
            public void setHttpClient(HttpClient client) {
                this.currentClient = client;
            }

            @Override
            public void abortCurrentRequest() {
                // Called on Gatling timeout - abort mid-request
                if (currentClient != null) {
                    currentClient.abort();
                }
            }
        };
    }
}
```

**Failure Handling:**
- On first Karate assertion failure, execution stops immediately
- Partial results (successful requests before failure) are reported to Gatling
- Failed request is reported with KO status and error message

**Timeout Handling:**
- If Gatling scenario timeout is reached, `abortCurrentRequest()` is called
- In-flight HTTP request is aborted via `HttpClient.abort()`
- Prevents thread starvation from slow responses

### 3.4 Pause Implementation (Gatling Model)

Uses Gatling's non-blocking pause mechanism via `karate.pause()`:

```java
// In PerfHook implementation
@Override
public void pause(Number millis) {
    // Gatling's non-blocking pause - waits without consuming threads
    try {
        Await.result(Future.never(), Duration.apply(millis.longValue(), TimeUnit.MILLISECONDS));
    } catch (TimeoutException e) {
        // Expected - timeout is the pause completion signal
    }
}
```

**Usage in Karate Features:**
```gherkin
# Non-blocking pause - integrates with Gatling
* karate.pause(5000)
```

**Note:** `karate.pause()` is preferred over `Thread.sleep()` as it:
- Integrates with Gatling's scheduler
- Does not block carrier threads
- Works correctly in both Gatling and non-Gatling modes

---

## 4. Custom Performance Event Capture

Add `PerfContext` interface to karate-core (same as v1) with `KarateJs` implementing it.

### 4.1 Add PerfContext interface to karate-core

```java
// io/karatelabs/core/PerfContext.java
package io.karatelabs.core;

/**
 * Interface for capturing custom performance events.
 * Used primarily for Gatling integration but designed as a NO-OP
 * when not in a performance testing context.
 */
public interface PerfContext {
    void capturePerfEvent(String name, long startTime, long endTime);
}
```

### 4.2 KarateJs implements PerfContext

```java
// In KarateJs.java
public class KarateJs extends KarateJsBase implements PerfContext {

    private Consumer<PerfEvent> perfEventHandler;

    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        if (perfEventHandler != null) {
            perfEventHandler.accept(new PerfEvent(name, startTime, endTime));
        }
        // NO-OP when perfEventHandler is null (normal Karate execution)
    }

    public void setPerfEventHandler(Consumer<PerfEvent> handler) {
        this.perfEventHandler = handler;
    }
}

// Simple data class
public record PerfEvent(String name, long startTime, long endTime) {}
```

### 4.3 Gatling module hooks the handler

```java
// In KarateFeatureAction, before running feature
karateJs.setPerfEventHandler(event -> {
    if (!silent) {
        statsEngine.logResponse(
            session.scenario(), session.groups(),
            event.name(), event.startTime(), event.endTime(),
            Status.OK, 200, null
        );
    }
});
```

### 4.4 Usage in feature files (identical to v1)

```gherkin
Scenario: Custom RPC
  * def result = Java.type('mock.MockUtils').myRpc({ sleep: 100 }, karate)
```

```java
// MockUtils.java - clean typed API (same as v1!)
public static Map<String, Object> myRpc(Map<String, Object> args, PerfContext context) {
    long start = System.currentTimeMillis();
    // ... custom logic (database, gRPC, etc.) ...
    long end = System.currentTimeMillis();

    context.capturePerfEvent("myRpc", start, end);
    return Map.of("success", true);
}
```

This approach:
- Provides clean `PerfContext` interface for users (same as v1)
- `KarateJs` implements `PerfContext` so `karate` object can be passed directly
- NO-OP when not in Gatling context (perfEventHandler is null)
- Gatling module sets the handler before feature execution
- **100% API compatible with v1 user code**

---

## 5. Session Variable Flow

Gatling session variables and chained Karate variables are exposed under the
`__gatling` and `__karate` maps respectively — access is **prefix-only**.

> **Breaking change vs v1 (intentional).** v1 also flattened these to top-level
> variables, so `userId` resolved as well as `__gatling.userId`. v2 drops the
> flattening: only `__gatling.userId` / `__karate.catId` work. This is a
> deliberate breaking change — flattening top-level re-introduces name
> collisions with Karate built-ins (a Gatling attribute named `request` /
> `response` would shadow them) and the hashCode / circular-reference hazards of
> placing JS-backed objects directly into the Gatling Session (the bridge keeps
> them under a single `__karate` key via a mutable map view specifically to
> avoid this). **User-facing docs / migration guide must call this out when the
> tracking fix lands.**

```
Gatling Session                    Karate Context
─────────────────                  ──────────────
userId: 1                    →     __gatling.userId
name: "Fluffy"              →     __gatling.name
__karate: { catId: 123 }    ←     catId (from feature)
```

### Access patterns in features:
```gherkin
# Access Gatling variables
* def userId = karate.get('__gatling.userId', 0)

# Access previous Karate results
* def catId = __karate.catId
```

---

## 6. Test Implementation

### 6.1 Single Comprehensive Simulation

Consolidate v1's multiple simulations into one comprehensive test.

**Perf-Optimized Defaults:**
- HTML reports disabled (Gatling handles reporting)
- Logging reduced (only errors/warnings)
- Fresh feature parse per scenario (no caching)

```java
package io.karatelabs.gatling;

public class GatlingSimulation extends Simulation {

    // Start mock server using v2's Server class (dogfooding)
    static {
        MockServer.start();
    }

    KarateProtocolBuilder protocol = karateProtocol(
        uri("/cats/{id}").nil(),
        uri("/cats").pauseFor(method("get", 10), method("post", 20))
    );

    // Feeder for data-driven tests
    Iterator<Map<String, Object>> feeder = Stream.iterate(0, i -> i + 1)
        .map(i -> Map.<String, Object>of("name", "Cat" + i))
        .iterator();

    // Scenario 1: Basic CRUD
    ScenarioBuilder crud = scenario("CRUD Operations")
        .exec(karateFeature("classpath:features/cats-crud.feature"));

    // Scenario 2: Chained with feeders
    ScenarioBuilder chained = scenario("Chained Operations")
        .feed(feeder)
        .exec(karateSet("name", s -> s.getString("name")))
        .exec(karateFeature("classpath:features/cats-create.feature"))
        .exec(karateFeature("classpath:features/cats-read.feature"));

    // Scenario 3: Silent warm-up
    ScenarioBuilder warmup = scenario("Warm-up")
        .exec(karateFeature("classpath:features/cats-crud.feature").silent());

    {
        setUp(
            warmup.injectOpen(atOnceUsers(1)),
            crud.injectOpen(rampUsers(5).during(5)),
            chained.injectOpen(rampUsers(3).during(5))
        ).protocols(protocol.build());
    }
}
```

### 6.2 Test Features

Port and simplify v1 features:

```gherkin
# features/cats-crud.feature
Feature: CRUD Operations

Scenario: Create and read cat
  Given url baseUrl
  And path 'cats'
  And request { name: 'Fluffy' }
  When method post
  Then status 201
  * def catId = response.id

  Given path 'cats', catId
  When method get
  Then status 200
  And match response.name == 'Fluffy'
```

### 6.3 Mock Server (v2 Server class - dogfooding)

Uses v2's `io.karatelabs.core.Server` for the test mock:

```java
package io.karatelabs.gatling;

import io.karatelabs.core.Server;

public class MockServer {
    private static Server server;

    public static void start() {
        // Using v2's Server class - dogfooding our own mock server
        server = Server.builder()
            .feature("classpath:mock/mock.feature")
            .build();
        System.setProperty("mock.port", String.valueOf(server.getPort()));
    }

    public static void stop() {
        if (server != null) server.stop();
    }
}
```

This approach validates both karate-gatling and v2's mock server under load.

---

## 7. Features Checklist (V1 Parity)

### Core Features
- [x] `karateProtocol()` with URI patterns
- [x] `karateFeature()` with tag selection
- [x] `karateSet()` for variable injection
- [x] `pauseFor()` method-specific pauses
- [x] Custom `nameResolver`
- [x] `Runner.Builder` exposure via `protocol.runner`
- [x] Silent mode (`.silent()`)

### Session Management
- [x] `__gatling` map passed to Karate
- [x] `__karate` map returned to Gatling
- [x] Feature variable chaining
- [x] Feeder integration

### Metrics & Reporting
- [x] HTTP request timing to Gatling StatsEngine
- [x] Status code reporting
- [x] Error message capture
- [x] Custom perf event capture (via PerfContext)

### Caching
- [x] Leverage v2's `Suite.getCallSingleCache()` — protocol-scoped, shared across virtual users
- [x] Leverage v2's `Suite.getCallOnceCache()` — protocol-scoped, per-feature, shared across virtual users
- [x] Same for `Suite.getSetupOnceCache()`

### Configuration
- [x] `karateEnv` via Runner.Builder
- [x] `configDir` via Runner.Builder
- [x] `systemProperty` via Runner.Builder
- [x] Tag filtering

---

## 8. Implementation Order

### Phase 0: karate-core Prerequisites ✅ DONE
- ✅ `HttpClientFactory` interface added to karate-core
- ✅ callOnce race condition fixed (ReentrantLock)
- ✅ callOnce scope fixed (feature-level)

### Phase 1: Foundation ✅ COMPLETE
3. Create `karate-gatling` module with pom.xml
4. Implement `KarateProtocol` and `KarateProtocolBuilder`
5. Implement `MethodPause` and `KarateUriPattern`

### Phase 2: Core Actions ✅ COMPLETE
6. Implement `KarateFeatureAction` with Runner.runFeature integration
7. Implement `KarateFeatureBuilder` with `.silent()`
8. Implement `KarateSetAction` and builder
9. Implement `KarateDsl` public API
10. ~~Implement `PooledHttpClientFactory` for Gatling~~ (deferred - use default client first)

### Phase 3: Testing ✅ COMPLETE
11. Create mock server using v2 MockServer class
12. Port test features (simplified)
13. Create `GatlingSimulation` comprehensive test
14. Add `Runner.runFeature(path, arg)` to karate-core
15. Add `Suite.init()` for config loading
16. Fix embedded expressions in request body (`StepExecutor.java`)

### Phase 4: Polish
14. Port README.md with updated examples (Java-only, no Scala DSL)
15. Add to parent pom.xml modules
16. CI/CD integration

### Phase 5: Standalone CLI Support (Non-Java Teams)
17. Add `CommandProvider` SPI to karate-core for dynamic subcommand discovery
18. Implement `PerfCommand` in karate-gatling (`karate perf`)
19. Implement dynamic simulation generation from feature files
20. Create `karate-gatling-bundle.jar` fatjar (Gatling + Scala + karate-gatling)
21. Document standalone CLI usage

### Phase 6: Profiling & Validation
22. Create overhead comparison test (v2 karate-gatling vs plain Gatling)
23. Port v1's `examples/profiling-test` for memory leak detection
24. Run extended load tests to validate:
    - No memory leaks in HTTP client pooling
    - No memory leaks in v2 mock server under sustained load
    - Overhead within acceptable range (< 5% vs plain Gatling)
25. Document profiling methodology and results

---

## 9. Package Changes

| V1 Package | V2 Package |
|------------|------------|
| `com.intuit.karate.gatling` | `io.karatelabs.gatling` |
| `com.intuit.karate.gatling.javaapi` | `io.karatelabs.gatling` (merged) |
| `com.intuit.karate.core.ScenarioRuntime` | `io.karatelabs.core.ScenarioRuntime` |
| `com.intuit.karate.http.HttpRequest` | `io.karatelabs.http.HttpRequest` |
| `com.intuit.karate.Runner` | `io.karatelabs.core.Runner` |
| `com.intuit.karate.PerfContext` | Replaced by JsCallable pattern |

---

## 10. Migration Guide (for users)

### Import Changes
```java
// V1
import com.intuit.karate.gatling.javaapi.*;
import static com.intuit.karate.gatling.javaapi.KarateDsl.*;

// V2
import io.karatelabs.gatling.*;
import static io.karatelabs.gatling.KarateDsl.*;
```

### Simulation Class
```java
// No change - still extends Gatling's Simulation
public class MySimulation extends Simulation { ... }
```

### Custom Perf Events
```java
// V1 - works unchanged in V2!
public static void myRpc(Map args, PerfContext ctx) {
    ctx.capturePerfEvent("name", start, end);
}
```
**No changes needed** - just update the import from `com.intuit.karate.PerfContext` to `io.karatelabs.core.PerfContext`.

### Session Variable Access (breaking change)

v1 flattened Gatling and chained variables to the top level, so unprefixed access
worked. v2 namespaces them under `__gatling` / `__karate` only — update any feature
files that relied on the unprefixed form.

```gherkin
# v1 - unprefixed access worked
* def id = userId
* def catId = catId

# v2 - prefix required
* def id = __gatling.userId
* def catId = __karate.catId
```

Also note: Gatling variables are injected as call-args, which apply *after*
`karate-config.js` runs — they are not available during config initialization.

---

## 11. Files to Create/Modify

### karate-core (modifications)
| File | Purpose | Status |
|------|---------|--------|
| `HttpClientFactory.java` | Interface for HTTP client creation | ✅ Done |
| `DefaultHttpClientFactory.java` | Default per-instance factory | ✅ Done |
| `StepExecutor.java` | callOnce race condition + scope fix | ✅ Done |
| `FeatureRuntime.java` | callOnce lock, feature-level cache | ✅ Done |
| `KarateJs.java` | Accept HttpClientFactory | ✅ Done |
| `PerfContext.java` | Interface for custom perf event capture | Phase 2 |
| `PerfEvent.java` | Perf event data record | Phase 2 |
| `CommandProvider.java` | SPI for dynamic subcommand discovery | Phase 5 |
| `Main.java` | ServiceLoader discovery for CommandProvider | Phase 5 |

### karate-gatling (new module)
| File | Purpose |
|------|---------|
| `pom.xml` | Maven module configuration |
| `KarateDsl.java` | Public API entry point |
| `KarateProtocol.java` | Gatling protocol |
| `KarateProtocolBuilder.java` | Protocol builder |
| `KarateFeatureAction.java` | Feature execution with PerfHook |
| `KarateFeatureBuilder.java` | Feature action builder with `.silent()` |
| `KarateSetAction.java` | Variable injection |
| `KarateUriPattern.java` | URI pattern + pause |
| `MethodPause.java` | Method/pause record |
| `PooledHttpClientFactory.java` | Gatling pooled connection factory |
| `GatlingSimulation.java` | Comprehensive test |
| `MockServer.java` | Test mock server (uses v2 Server) |
| `features/*.feature` | Test features |
| `mock/mock.feature` | Mock implementation |
| `README.md` | Documentation (Java-only examples) |
| `GatlingCommandProvider.java` | ServiceLoader provider for `perf` command (Phase 5) |
| `PerfCommand.java` | CLI command implementation (Phase 5) |
| `DynamicSimulation.java` | Runtime simulation generator (Phase 5) |
| `ProfilingSimulation.java` | Overhead comparison test (Phase 6) |
| `META-INF/services/...` | ServiceLoader registration (Phase 5) |

**Note:** No Scala files needed - Java DSL works for both Java and Scala users.

---

## 12. Standalone CLI Support (Phase 5)

Enable non-Java teams to run performance tests without Maven/Gradle.

### 12.1 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Rust Launcher (karate binary)                              │
│  - Constructs classpath: karate.jar + ext/*.jar             │
│  - Delegates to Java CLI                                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Java CLI (Main.java)                                       │
│  - Uses ServiceLoader to discover CommandProvider           │
│  - Finds GatlingCommandProvider from karate-gatling-bundle  │
│  - Registers 'perf' subcommand                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  PerfCommand (in karate-gatling)                            │
│  - Generates dynamic Gatling simulation from features       │
│  - Runs Gatling with specified load profile                 │
└─────────────────────────────────────────────────────────────┘
```

### 12.2 CommandProvider SPI (in karate-core)

```java
// io/karatelabs/cli/CommandProvider.java
package io.karatelabs.cli;

/**
 * SPI for modules to register CLI subcommands.
 * Discovered via ServiceLoader when JARs are on classpath.
 */
public interface CommandProvider {
    String getName();           // e.g., "perf"
    String getDescription();    // e.g., "Run performance tests"
    Object getCommand();        // PicoCLI command instance
}
```

```java
// In Main.java - discover and register commands
ServiceLoader<CommandProvider> providers = ServiceLoader.load(CommandProvider.class);
for (CommandProvider provider : providers) {
    spec.addSubcommand(provider.getName(), provider.getCommand());
}
```

### 12.3 PerfCommand (in karate-gatling)

Features-only scope - generates dynamic simulation from feature files:

```java
@Command(name = "perf", description = "Run performance tests with Gatling")
public class PerfCommand implements Callable<Integer> {

    @Parameters(description = "Feature files or directories")
    List<String> paths;

    @Option(names = {"-u", "--users"}, description = "Number of concurrent users")
    int users = 1;

    @Option(names = {"-d", "--duration"}, description = "Test duration (e.g., 30s, 5m)")
    String duration = "30s";

    @Option(names = {"-r", "--ramp"}, description = "Ramp-up time (e.g., 10s)")
    String rampUp = "0s";

    @Option(names = {"-t", "--tags"}, description = "Tag expression filter")
    String tags;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    String outputDir = "target/gatling";

    @Option(names = {"--format"}, description = "Report format: html (default) or json")
    String format = "html";

    @Override
    public Integer call() {
        // Generate dynamic simulation from features
        // (No --simulation option - features only for simplicity)
        return runDynamicSimulation(paths, users, duration, rampUp, tags, format);
    }
}
```

**Report Formats:**
- `html` (default): Full Gatling HTML report with Highcharts visualizations
- `json`: Machine-readable JSON for CI/CD pipelines and external tools (Grafana, etc.)

### 12.4 Dynamic Simulation Generation

```java
// Generates Gatling simulation at runtime from feature files
public class DynamicSimulation extends Simulation {

    public DynamicSimulation() {
        // Read config from system properties (set by PerfCommand)
        String[] paths = System.getProperty("karate.perf.paths").split(",");
        int users = Integer.getInteger("karate.perf.users", 1);
        Duration duration = parseDuration(System.getProperty("karate.perf.duration", "30s"));
        Duration rampUp = parseDuration(System.getProperty("karate.perf.rampUp", "0s"));
        String tags = System.getProperty("karate.perf.tags");

        KarateProtocolBuilder protocol = karateProtocol();

        ScenarioBuilder scenario = scenario("Performance Test")
            .exec(karateFeature(paths).tags(tags));

        setUp(
            scenario.injectOpen(
                rampUsers(users).during(rampUp),
                constantUsersPerSec(users).during(duration)
            )
        ).protocols(protocol.build());
    }
}
```

### 12.5 CLI Usage Examples

```bash
# Setup: Download and install the Gatling bundle
# Option A: Manual download
curl -L https://github.com/karatelabs/karate/releases/download/v2.0.0/karate-gatling-bundle.jar \
  -o ~/.karate/ext/karate-gatling-bundle.jar

# Option B: Via karate CLI (future)
karate plugin install gatling

# Run performance tests
karate perf features/api.feature

# With load profile
karate perf --users 10 --duration 60s --ramp 10s features/

# Filter by tags
karate perf --users 5 --duration 30s -t @smoke features/

# JSON output for CI/CD
karate perf --users 10 --duration 30s --format json features/
```

**Error Handling:**
```bash
# If bundle JAR is missing:
$ karate perf features/
Error: Gatling bundle not found.
Run: karate plugin install gatling
Or download manually from: https://github.com/karatelabs/karate/releases
```

### 12.6 karate-pom.json Support

```json
{
  "paths": ["features/"],
  "tags": ["@api"],
  "perf": {
    "users": 10,
    "duration": "60s",
    "rampUp": "10s",
    "output": "target/gatling-reports",
    "format": "html"
  }
}
```

```bash
# Reads perf config from karate-pom.json
# Config discovery matches 'karate test' behavior
karate perf
```

### 12.7 Bundle JAR Build

```xml
<!-- In karate-gatling/pom.xml -->
<profile>
    <id>bundle</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <configuration>
                    <artifactSet>
                        <includes>
                            <include>io.karatelabs:karate-gatling</include>
                            <include>io.gatling:*</include>
                            <include>io.gatling.highcharts:*</include>
                            <include>org.scala-lang:*</include>
                            <!-- Gatling transitive deps -->
                        </includes>
                        <excludes>
                            <!-- Don't include karate-core - user already has it -->
                            <exclude>io.karatelabs:karate-core</exclude>
                            <exclude>io.karatelabs:karate-js</exclude>
                        </excludes>
                    </artifactSet>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Build command:
```bash
mvn package -Pbundle -DskipTests
# Output: karate-gatling/target/karate-gatling-bundle.jar
```

### 12.8 Memory Configuration

Gatling may need more memory for high user counts. Users configure via:

```json
// ~/.karate/karate-cli.json
{
  "jvm_opts": "-Xmx2g"
}
```

Or PerfCommand could auto-adjust based on user count:
```java
// In PerfCommand
if (users > 100) {
    System.setProperty("karate.jvm.opts.extra", "-Xmx2g");
}
```

### 12.9 JRE Compatibility

JustJ (bundled JRE) provides Java 21 which is fully compatible with:
- Gatling 3.12.x (requires Java 11+) ✓
- Scala 3.x (requires Java 11+) ✓

No special JRE configuration needed.

### 12.10 CLI Testing Strategy

Testing the standalone CLI requires building JARs and manual verification.

#### Test Setup Script

Create `etc/test-gatling-cli.sh`:

```bash
#!/bin/bash
set -e

# Build all modules
echo "Building karate-core fatjar..."
mvn clean package -DskipTests -Pfatjar -pl karate-core -am

echo "Building karate-gatling bundle..."
mvn package -DskipTests -Pbundle -pl karate-gatling

# Setup test environment
TEST_HOME="home/.karate"
mkdir -p "$TEST_HOME/ext"

# Copy JARs
cp karate-core/target/karate.jar "$TEST_HOME/dist/"
cp karate-gatling/target/karate-gatling-bundle.jar "$TEST_HOME/ext/"

echo "Test environment ready at $TEST_HOME"
```

#### Manual Test Scenarios

| Test | Command | Expected |
|------|---------|----------|
| Help displayed | `karate perf --help` | Shows perf command options |
| Basic run | `karate perf home/test-project/features/hello.feature` | Runs Gatling, generates report |
| With users | `karate perf -u 5 features/` | 5 concurrent users |
| With duration | `karate perf -u 3 -d 10s features/` | Runs for 10 seconds |
| With ramp | `karate perf -u 10 -r 5s -d 30s features/` | 5s ramp to 10 users |
| With tags | `karate perf -t @smoke features/` | Filters by tag |
| From pom | `karate perf` (with karate-pom.json) | Reads perf config from pom |
| Report output | Check `target/gatling/` | HTML report generated |

#### Test Project Structure

```
home/test-project/
├── karate-pom.json
├── karate-config.js
├── features/
│   ├── hello.feature      # Simple GET request
│   └── crud.feature       # CRUD operations
└── mock/
    └── mock.feature       # Mock server
```

#### Sample karate-pom.json for Testing

```json
{
  "paths": ["features/"],
  "perf": {
    "users": 5,
    "duration": "20s",
    "rampUp": "5s"
  }
}
```

#### Test with Claude Code

When developing, use Claude Code to:

1. **Build and test incrementally:**
   ```
   # Ask Claude to build and run a specific test
   "Build the gatling module and test karate perf --help"
   ```

2. **Verify Gatling reports:**
   ```
   # Ask Claude to check report output
   "Run karate perf with 3 users for 10s and verify the HTML report was generated"
   ```

3. **Debug issues:**
   ```
   # If something fails
   "The karate perf command failed with [error]. Check the classpath and ServiceLoader registration"
   ```

#### Verification Checklist

- [ ] `karate --help` shows `perf` subcommand when bundle JAR present
- [ ] `karate perf --help` shows all options (no --simulation)
- [ ] Basic feature execution works
- [ ] Load profile options work (users, duration, ramp)
- [ ] Tag filtering works
- [ ] karate-pom.json perf section is read
- [ ] Gatling HTML reports generated (default format)
- [ ] JSON format works (--format json)
- [ ] Error messages are clear when bundle JAR missing

---

## 13. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Gatling 3.12 API changes | Review Gatling changelog, test thoroughly |
| Bundle JAR size (~50-80MB) | Document size, consider optional download, compress |
| ServiceLoader not finding provider | Test classpath construction, clear error messages |
| Scala 3 compilation issues | Use latest scala-maven-plugin, test cross-compilation |
| callOnce race condition | Fix in karate-core before Gatling implementation |
| PerfHook timing accuracy | Compare with v1 metrics in Phase 6 profiling |
| Session variable conflicts | Document reserved keys, validate on set |
| HTTP client memory leaks | Validate in Phase 6 extended load tests |

---

## 14. Operational Notes

### 14.1 Timeout Handling

When Gatling's scenario timeout is reached:
1. `PerfHook.abortCurrentRequest()` is called
2. In-flight HTTP request is aborted via `HttpClient.abort()`
3. Scenario is marked as failed
4. Partial results are still reported

This prevents thread starvation from slow/hanging responses.

### 14.2 Resource Cleanup

**JVM Garbage Collection is sufficient** for resource cleanup. However, best practices:
- Use try-with-resources for explicit cleanup in custom Java code
- Avoid holding large objects across feature executions
- HTTP connections are managed by the pooled factory

### 14.3 Feature Parsing

**Fresh parse per scenario** - no feature caching:
- Each Gatling virtual user parses features independently
- Ensures isolation between concurrent executions
- Slightly higher CPU usage but simpler implementation

### 14.4 Parallel Execution

**Sequential within scenario, parallel across scenarios:**
- Features within a single Gatling scenario execute sequentially
- Use multiple Gatling scenarios for parallelism
- No `karateParallel()` DSL - keep it simple

Example:
```java
// CORRECT: Parallel via Gatling scenarios
ScenarioBuilder cats = scenario("Cats").exec(karateFeature("cats.feature"));
ScenarioBuilder dogs = scenario("Dogs").exec(karateFeature("dogs.feature"));
setUp(
    cats.injectOpen(rampUsers(10).during(10)),
    dogs.injectOpen(rampUsers(10).during(10))
);
```

### 14.5 Gatling Edition

**Open-source Gatling only.** For Gatling Enterprise integration:
- Export JSON reports from karate-gatling
- Import into Gatling Enterprise separately
- No built-in SDK integration

### 14.6 Dynamic Load Adjustment

Gatling handles dynamic load natively via:
- `constantUsersPerSec(rate).during(duration).randomized()`
- `rampUsersPerSec(rate1).to(rate2).during(duration)`
- Throttling: `throttle(reachRps(100).in(10))`

No Karate-specific dynamic load adjustment needed.

### 14.7 Request Timing

Request timing **includes connection pool wait time**:
- Reflects real user experience
- Measures from `HttpClient.invoke()` call to response
- Includes SSL handshake, connection acquisition, network round-trip

### 14.8 Silent Mode Behavior

When `.silent()` is set on a `karateFeature()`:
- **Gatling metrics**: Not reported to StatsEngine
- **Karate HTML reports**: Already disabled in perf mode
- **Logging**: Reduced to errors/warnings only — as it is for every feature in this lane (§14.9),
  so `.silent()` adds nothing on that axis
- **Feature execution**: Runs normally, just invisible to reports

Use for warm-up scenarios before actual load test.

### 14.9 Default Fidelity — BUILT

**The decision: under Gatling, assume the user wants no HTML report and no logging except on
errors.** Anything beyond that is opt-in.

**Already off** before this (`Runner.runFeature`, the Gatling lane): HTML report, console
summary, boot file, call-result retention. JSONL / JUnit / Cucumber are off by default anyway.
Report *writing* — the largest cost in the whole memory investigation — was already disabled.

**What was still on, and nothing read it:** the per-step log capture. For every request Karate
re-parsed the JSON body, pretty-printed it, ANSI-colourised it, wrapped it in sentinels,
appended it to a thread-local buffer and hung it off `StepResult.log`, where it was retained
and never read — `HttpLogger`'s own comment said the buffer exists "so HTML reports stay rich
regardless of SLF4J level", and under Gatling there is no HTML report.

**What was built.** A `capture` flag on `LogContext` (an instance field on the thread-local, so
no cross-Suite races), set per scenario in `ScenarioRuntime.call()` from
`Suite.isCaptureStepLogs()` — which answers "yes, unless this is a perf run" and is resolved
lazily, because `setPerfHook` runs after the Suite is built.
`Runner.Builder.captureStepLogs(boolean)` overrides it in either direction and is propagated
through the `runFeature` template.

The three things that made it more than a one-liner, and how each is honoured:

1. **The check happens before the string is built.** The threshold test inside
   `LogContext.log` is too late — the cost is already paid by the time it is reached. So
   `HttpLogger.logRequest` / `logResponse` now ask `LogContext.isCapturing(INFO)` up front and
   skip the whole block when nothing wants it. That question covers both axes — the capture
   flag *and* the report threshold — so a `configure logging = { report: 'warn' }` now stops
   the string being built rather than dropping it afterwards. The body, the expensive part, is
   built only for capture or SLF4J `TRACE`; `DEBUG` stops at the headers, as before.
   `LogContext.log` and `LogWriter` also drop their appends when capture is off, as the
   backstop for every other producer.
2. **`logReplay` switches capture back on.** It replays exactly this buffer, so replay without
   capture would be a feature that silently does nothing. `KarateProtocolBuilder.build()` sets
   `runner.captureStepLogs(true)` whenever the mode is not `OFF` — which also retires the
   documented footgun that replay was bounded by the report threshold. Replay stays free when
   `OFF`: nothing is touched there, so `captureStepLogs` remains the user's to set.
3. **`print` / `karate.log()` still reach the console.** They go through `LogWriter`, whose
   SLF4J call is deliberately unconditional — only the buffer append is gated. Verified, not
   assumed: `StepLogCaptureTest.printStillReachesTheConsoleWithCaptureOff` runs a feature in
   the perf lane with an appender on `karate.scenario`.

**Measured**, `gatling-http-karate --iterations 2000`, against the pre-change baseline and the
throwaway-guard run that predicted it (PROFILING.md §6):

| site | before | guard | after (3 runs) |
|---|---:|---:|---:|
| `Json.parseLenient` | 3.7% | 0.9% | — |
| `LogContext.log` | 1.6% | — | — |
| `LogContext.collect` | 1.2% | — | — |
| `ApacheHttpClient.buildResponse` | — | 3.5% | 1.8% / 6.6% / 5.2% |

The three logging sites are gone from the profile entirely — `parseLenient` does not even
reach the digest's top-25 cutoff (~1.1%) — which is the ~6.5 points the guard run predicted.
`buildResponse` is what is left of reading the response body, and its share bounces across
runs because allocation sampling attributes the same `byte[]` work differently; it is noise,
not a trend. This row previously read 8.6% "with capture", which traces to the "~9%" in
PROFILING.md §6's `http` pair paragraph rather than to the pre-change run measured here (that
digest does not list `buildResponse` in its top-25 at all) — hence the blank.

**What did not move: the total.** Sampled allocation for the workload stays in the 460–560 MB
band §6 records, before and after. Six or seven points of a ~500 MB profile is inside the
run-to-run spread of a sampled recording, and at 2000 iterations a large slice of every run is
fixed startup (`initHttpClient`, parser init, config eval). The claim this supports is "those
sites are gone", not "the workload allocates measurably less" — that would need a longer run,
or `--duration`, which is now built (`during()` in the injection profile).

**Two things the flag does not cover**, both worth knowing before quoting "nothing is built":

- **`karate.http()` called directly in `karate-config.js`.** Config JS is evaluated in the
  `ScenarioRuntime` constructor, before `call()` installs the fresh `LogContext` the flag is
  set on, so those blocks are still built (and then dropped by the gated `appendCaptured`). A
  `karate-config-perf.js` that fetches a token that way pays the full capture cost per
  scenario. A `karate.call` / `callSingle` of a *feature* does go through `call()` and is
  gated; only direct config-time HTTP escapes.
- **Embeds and synthetic steps.** `LogContext.embed` / `step` still attach to the
  `StepResult` with capture off, so a perf feature calling `karate.embed(screenshot)` retains
  every image for a report that will not be written. Pre-existing, and the flag is scoped to
  `StepResult.log` on purpose — but it is the same class of waste.

Separate, and since done: the per-scenario Logback level snapshot/restore
(`LogContext.captureRuntimeLevels` + `setLevelOn`), a reflective walk over eight logger names
on every scenario, to put back levels that only `configure logging = { console: ... }` ever
changes. `snapshot()` is now lazy — it registers on a thread-local chain, and
`setRuntimeLogLevel` captures the "before" for every live snapshot in one walk just before it
changes anything. Worth 10% of the `gatling-null-karate` profile, with the total moving to
match; see PROFILING.md §9.

### 14.10 Failure Visibility

A Gatling run has no HTML report and no console summary, so a failure has exactly two surfaces —
and both must name **where** and **why**:

- **The KO message** (`ScenarioResult.getPerfFailureMessage()`, attached to the perf event in
  `ScenarioRuntime`): `path.feature:LINE step text - reason`. Only the reason's *first line* — Gatling
  groups its errors table by this string, so the full match diff (per-virtual-user actual values)
  would make every KO a unique row.
- **The failure log** (`KarateExecutor`): the same summary line, then the IDE-clickable absolute
  location, the assertion comment, and the full diff.

### 14.11 Log Replay

Per-step Karate output (HTTP blocks, `print`) is captured into `FeatureResult` regardless of the
Logback level — `LogContext` has its own threshold. `LogReplayer` uses that to hold each feature's
output and release it only when a feature fails, which is how a quiet load run can still explain a
failure that happened three features into a scenario.

- Configured on the protocol: `logReplay(OFF|FAILED|ALL)`, `logReplayLevel`, `logReplayLimit`.
- Asking for replay turns the capture on — in perf mode it is otherwise off, since nothing else
  reads it (§14.9). Replay is the reader, so `KarateProtocolBuilder.build()` sets
  `runner.captureStepLogs(true)` for any mode but `OFF`.
- Retention rides the **Gatling Session** (`__karateLog`), not a thread-local — Gatling is free to
  run a later `exec()` of the same scenario on a different thread. Like `__karate`, the key is
  excluded from the `__gatling` map.
- The buffer is immutable and bounded; it is cleared after every replay, and a replay states how
  many entries the cap dropped.

---

### 14.12 Injector health — DESIGNED, NOT BUILT

**The problem this exists for is specific, and it is the sharpest finding from the parity work in
[PROFILING.md §10](./PROFILING.md).** Karate's per-execution overhead sits *between* `PerfEvent`
brackets — the reported response time is the HTTP call only, built inside the client as
`PerfEvent(start = response.getStartTime(), end = start + responseTime)`. Suite construction,
config evaluation, feature parse and `match` all fall outside it.

That is good news for fidelity: **the percentiles in a Gatling report are the server's, not the
client's.** It is also the hazard. Because the overhead is outside the brackets, an
under-provisioned injector shows up as **a throughput shortfall with clean-looking latencies** —
queue-for-a-thread time never reaches a percentile. Nothing in the report looks wrong, and the
system under test is silently under-loaded. A detector is the only way that becomes visible.

#### Which signals may gate, and which must not

| Signal | What it measures | Gate on it? |
|---|---|---|
| Process CPU load against available cores | the machine | **yes** |
| **Heartbeat scheduling jitter** — a daemon thread sleeping ~100 ms, recording actual-vs-intended | the machine, including cases CPU% misses | **yes** |
| Achieved vs intended arrival rate (open model only) | offered load directly | yes, where available |
| Per-iteration residue: `execute()` elapsed − Σ `PerfEvent` durations | **the work** | **no — report only** |

**The residue is the tempting one and it must not gate.** Karate legitimately spends on the order
of a millisecond or two per iteration (0.6 ms on Apple silicon, 1.8 ms on Graviton3 — the figure is
machine-specific), so any residue threshold fires on a perfectly healthy run. It is worth
*reporting*, because it attributes Karate's own overhead directly instead of by subtracting two
throughputs, and it is [PROFILING.md §9](./PROFILING.md)'s unbuilt "per-iteration residue" item.

**The heartbeat is the one not to skip.** CPU% alone misses the case that actually bites in cloud
CI: a container throttled by its cgroup quota reports *low* CPU while being unable to run anything
on time. Jitter catches that directly, and it is about fifteen lines.

#### Behaviour

- **Warn always**, from a soft threshold, with the numbers. Rate-limited per breach window — a
  saturated 30-minute soak must not emit thousands of lines, or people stop reading them.
- **Fail only on sustained, unambiguous saturation** — a band chosen so that it is essentially
  never a false positive. A false failure at the end of a long, expensive run is the fastest way
  to get the whole check disabled globally.
- **One config to switch it off**, on `KarateProtocolBuilder` alongside `logReplay` etc.
- **A verdict in the run summary either way**, so a passing run still records that it had headroom.

#### Where it goes

`KarateExecutor.execute()` is the single Java-side seam wrapping the whole per-iteration body, so
it is also where the residue timing point belongs. Config fits `KarateProtocolBuilder`'s existing
fluent style.

#### How to test it — and the one case a JVM flag cannot reach

The two gating signals need different simulations, which is itself the argument for having both:

| To exercise | How |
|---|---|
| **CPU saturation** | `-XX:ActiveProcessorCount=1`. Makes `availableProcessors()` return 1 — the exact denominator the check divides by — and genuinely shrinks the JVM's GC and ForkJoin pools, so real contention follows. No privileges needed. |
| **Scheduler jitter** | A tiny heap (`-Xmx32m`) on an allocating workload: near-continuous GC makes a 100 ms heartbeat overshoot badly. |
| **cgroup throttling** | **Not reachable by any JVM flag.** `systemd-run --scope -p CPUQuota=25% …`, or a container with `--cpus=0.25`. |
| The real phenomenon | Raise the virtual-user count until the injector saturates. |

**The third row is the one that matters, and it is easy to skip.** A throttled container reports
*low* CPU while being unable to run anything on time — that is precisely the case CPU% misses and
the heartbeat exists for. A test suite that only uses JVM flags will pass without ever exercising
the signal that justified the design, so this case has to run on Linux, not on a developer laptop.

Note the profiling harness currently passes `-D` system properties through to the child JVM but
**not arbitrary `-XX` flags**, so the first two rows are not one-liners there yet.

#### Two things to design around

- **Containers.** Use `getProcessCpuLoad()` (normalised to available processors) rather than
  system-wide load, which in a container can reflect the host. `availableProcessors()` is
  cgroup-aware on modern JDKs, but verify the exact semantics on the targeted JDKs before building
  a threshold on it.
- **The heartbeat thread must be a platform thread**, and its jitter describes the whole JVM.

## 15. Implementation Progress

### 15.1 Current Status

**Phase 1: Foundation - COMPLETE** ✅

| File | Status | Notes |
|------|--------|-------|
| `pom.xml` | ✅ Done | Gatling 3.12.0, Scala runtime deps |
| `MethodPause.java` | ✅ Done | Simple record for method/pause |
| `KarateUriPattern.java` | ✅ Done | URI pattern with Builder |
| `KarateProtocol.java` | ✅ Done | Implements `io.gatling.core.protocol.Protocol` |
| `KarateProtocolBuilder.java` | ✅ Done | Protocol DSL builder |
| `KarateFeatureAction.java` | ✅ Done | Feature execution logic using v2 Suite API |
| `KarateFeatureBuilder.java` | ✅ Done | ActionBuilder with SessionHookBuilder |
| `KarateSetAction.java` | ✅ Done | Session variable setter |
| `KarateSetBuilder.java` | ✅ Done | ActionBuilder with SessionHookBuilder |
| `KarateDsl.java` | ✅ Done | Public API entry point |
| Parent `pom.xml` | ✅ Done | Module added |

**Module compiles successfully with `mvn compile`**

### 15.2 Scala Interop Solution

**Problem Solved:** Gatling's `ActionBuilder.asScala()` requires returning a Scala `ActionBuilder`. The solution uses Gatling's `SessionHookBuilder` with direct instantiation of Scala `Success` case class.

**Implementation Pattern:**
```java
@Override
public io.gatling.core.action.builder.ActionBuilder asScala() {
    Function<Session, Session> sessionFunc = toSessionFunction();
    // Create Scala Function1 that wraps Java function
    scala.Function1<io.gatling.core.session.Session,
                    io.gatling.commons.validation.Validation<io.gatling.core.session.Session>> scalaFunc =
            scalaSession -> {
                Session javaSession = new Session(scalaSession);
                Session result = sessionFunc.apply(javaSession);
                // Direct instantiation of Scala Success case class
                return new io.gatling.commons.validation.Success<>(result.asScala());
            };
    return new io.gatling.core.action.builder.SessionHookBuilder(scalaFunc, true);
}
```

**Key insight:** While Scala companion object methods like `Validation.success()` are not accessible from Java, the `Success` case class has a public constructor that works from Java.

### 15.3 Files Created

```
karate/karate-gatling/
├── pom.xml                    # Maven module config
└── src/main/java/io/karatelabs/gatling/
    ├── KarateDsl.java           # Public API entry point
    ├── KarateProtocol.java      # Protocol with URI pattern matching
    ├── KarateProtocolBuilder.java
    ├── KarateUriPattern.java    # URI pattern + pause config
    ├── MethodPause.java         # Method/pause record
    ├── KarateFeatureAction.java # Feature execution via v2 Suite
    ├── KarateFeatureBuilder.java # ActionBuilder implementation
    ├── KarateSetAction.java     # Session variable logic
    └── KarateSetBuilder.java    # ActionBuilder implementation
```

### 15.4 Phase 3: Testing - COMPLETE ✅

| File | Status | Notes |
|------|--------|-------|
| `CatsMockServer.java` | ✅ Done | Uses v2 MockServer with feature-based mock |
| `GatlingSimulation.java` | ✅ Done | Comprehensive test with feeders, chaining, silent mode |
| `GatlingSmokeSimulation.java` | ✅ Done | Smoke test runs during `mvn test` with assertions |
| `GatlingDslTest.java` | ✅ Done | DSL unit tests (protocol, patterns, builders) |
| `EngineBindingsTest.java` | ✅ Done | Unit tests for Runner.runFeature with arg map |
| `karate-config.js` | ✅ Done | Test config with mock port |
| `logback-test.xml` | ✅ Done | Reduced logging for Gatling |
| `mock/cats-mock.feature` | ✅ Done | CRUD mock for /cats endpoint |
| `features/cats-crud.feature` | ✅ Done | Basic CRUD operations |
| `features/cats-create.feature` | ✅ Done | Create with __gatling variables |
| `features/cats-read.feature` | ✅ Done | Read with __karate variables |
| `features/test-arg.feature` | ✅ Done | Variable accessibility test |

**Maven Integration:**
- `mvn test` runs JUnit tests + Gatling smoke simulation (no reports)
- `mvn verify -Pload-test` runs full Gatling simulation with HTML reports

### 15.5 karate-core Changes for Gatling

| Change | File | Notes |
|--------|------|-------|
| `Runner.runFeature(path, arg)` | `Runner.java` | Static method for Gatling to run features with variables |
| Result variables capture | `Runner.java` | Captures last scenario's variables for chaining |
| `Suite.init()` | `Suite.java` | Load config without running tests |
| Embedded expressions in request | `StepExecutor.java` | Fixed: `request { name: '#(var)' }` now resolves |
| Test for request expressions | `StepHttpTest.java` | Added `testRequestWithEmbeddedExpressions()` |

**Bug Fixed:** `executeRequest()` was not calling `processEmbeddedExpressions()` for JSON literals, causing `#(varName)` to be sent as literal strings.

**Variable Chaining Fixed:** `Runner.runFeature()` now captures result variables from the last executed scenario and sets them on `FeatureResult.resultVariables`. This enables `__karate` to pass variables between features in a Gatling chain.

### 15.6 Variable Flow Implementation

Variables are passed from Gatling to Karate via the `arg` map:

```
Gatling Session                    Runner.runFeature(path, arg)
─────────────────                  ────────────────────────────
karateSet("name", ...)       →     arg["__gatling"]["name"]
karateSet("age", ...)        →     arg["__gatling"]["age"]

FeatureRuntime(callArg=arg)  →     ScenarioRuntime.initEngine()
                                   └─ engine.put("__gatling", ...)
                                   └─ engine.put("__karate", ...)

Feature access:
  * def name = __gatling.name    ✅ Works
  * request { name: '#(name)' }  ✅ Works (after fix)
```

### 15.7 Next Steps

**Completed:**
- ✅ Variable chaining between features works
- ✅ Smoke simulation runs during `mvn test` (with HTML reports)
- ✅ DSL unit tests cover protocol, patterns, builders
- ✅ Gatling assertions work (failedRequests.count, etc.)
- ✅ Logging optimized for performance (TRACE/DEBUG levels)
- ✅ HTTP-level metrics via PerfHook integration
- ✅ URI pattern matching for request names (e.g., `GET /cats/{id}` instead of `GET /cats/2`)
- ✅ Upgraded to Gatling 3.13.5 and gatling-maven-plugin 4.20.16

**Remaining:**

1. **Phase 4: Polish**
   - Port README.md with Java-only examples
   - CI/CD integration

2. **Phase 5: Standalone CLI Support**
   - CommandProvider SPI for `karate perf` command
   - Dynamic simulation generation from feature files
   - Bundle JAR creation

3. **Phase 6: Profiling & Validation**
   - Overhead comparison tests
   - Memory leak detection under sustained load

### 15.8 Phase 3.5: Comprehensive CICD Tests - COMPLETE ✅

Combined load + validation tests for CI/CD pipelines. Replaces separate `load-test` profile.

**Run:** `mvn verify -pl karate-gatling -Pcicd`

**Test Scenarios:**

| Scenario | Purpose | Load | Expected |
|----------|---------|------|----------|
| Warm-up | Silent warm-up | 1 user | No metrics |
| CRUD Operations | Basic load test | 3 users ramp | 0% failures |
| Chained Operations | Variable passing | 2 users ramp | 0% failures |
| Java Interop | `PerfContext.capturePerfEvent()` | 2 users | 0% failures |
| Error Handling | Karate assertion failure | 2 users | gte(1) failures |

**Gatling Assertions:**
- `GET /cats/{id}`: 0% HTTP failures
- `POST /cats`: at least 1 failure (from Error Handling scenario)
- Global: at least 8 requests (validates all scenarios ran)

**Error Reporting:**
When a Karate assertion fails, the error message in Gatling reports includes the feature file path and line number:
```
> cats-create-fail.feature:14 response.name == 'WRONG_NAME...'    2 (100%)
```
This matches v1 behavior and makes debugging failures easy.

**Files:**
- `GatlingCicdSimulation.java` - Combined CICD simulation
- `TestUtils.java` - Java interop helper with `myRpc()`
- `features/custom-rpc.feature` - Java interop test
- `features/cats-create-fail.feature` - Intentional failure test

---

## 16. PerfHook Integration - COMPLETE ✅

HTTP metrics are now reported to Gatling's StatsEngine. The implementation follows the v1 pattern with a "deferred reporting" strategy where events are held until the next HTTP request or scenario end, allowing assertion failures to be attributed to the preceding HTTP request.

### 16.1 Architecture

**karate-core additions:**
- `PerfHook.java` - Interface for performance metric reporting
- `PerfEvent.java` - Data class for timing events
- `PerfContext.java` - Interface for custom perf event capture (e.g., DB, gRPC)
- `ScenarioRuntime` - Added `capturePerfEvent()` and `logLastPerfEvent()` methods
- `StepExecutor` - Captures HTTP request timing in `executeMethod()`
- `Runner.runFeature(path, arg, perfHook)` - Overload accepting PerfHook
- `Suite.perfHook()` - Builder method to set PerfHook
- `KarateJs` implements `PerfContext` for custom event capture

**karate-gatling additions:**
- `KarateScalaActions.scala` - Scala ActionBuilder and Action with StatsEngine access
- `KarateFeatureBuilder.java` - Updated to use Scala ActionBuilder

### 16.2 How It Works

```
Gatling executes scenario
    └─> KarateScalaAction.execute(session)
        └─> Creates PerfHook that reports to statsEngine
        └─> Runner.runFeature(path, arg, perfHook)
            └─> Suite.perfHook(hook)
            └─> FeatureRuntime.call()
                └─> ScenarioRuntime.call()
                    └─> StepExecutor.executeMethod()
                        └─> perfHook.getPerfEventName(request)
                        └─> http().invoke(method)
                        └─> runtime.capturePerfEvent(event)
                    └─> [more steps...]
                    └─> logLastPerfEvent(failureMessage)  // in finally block
```

### 16.3 Expected Output

With PerfHook integration and URI pattern matching, Gatling groups requests by pattern:

```
---- Requests ------------------------------------------------------------------
> Global                                                   (OK=12     KO=2     )
> POST /cats                                               (OK=5      KO=2     )
> GET /cats/{id}                                           (OK=5      KO=0     )
> custom-rpc                                               (OK=2      KO=0     )
---- Errors --------------------------------------------------------------------
> cats-create-fail.feature:14 response.name == 'WRONG...'  2 (100%)
```

Notes:
- Requests to `/cats/1`, `/cats/2`, etc. are grouped under `GET /cats/{id}` when the pattern is configured in `karateProtocol(uri("/cats/{id}").nil())`
- Failed assertions show the feature file path and line number for easy debugging

### 16.4 Custom Performance Events

The `PerfContext` interface allows capturing timing for non-HTTP operations:

```java
// In Java helper
public static Object myDatabaseCall(Map args, PerfContext karate) {
    long start = System.currentTimeMillis();
    // ... database operation ...
    long end = System.currentTimeMillis();
    karate.capturePerfEvent("database-query", start, end);
    return result;
}
```

```gherkin
# In feature file
* def result = Java.type('utils.DbHelper').myDatabaseCall({}, karate)
```

### 16.5 Testing

Run Gatling tests:
```bash
mvn test -pl karate-gatling           # Smoke test (no reports)
mvn verify -Pcicd -pl karate-gatling  # Full CICD test with HTML reports
```

HTML reports are generated in `target/gatling/`.

---

## Remaining TODOs

> Consolidated list of outstanding items. Phases 0-3 are complete.

### Phase 4: Polish
*(all items complete — see callsite changes in `Runner.Builder.callSingleCache(...)`,
`Suite.getCallOnceCache(featureKey)`, and `KarateProtocolBuilder.runner`)*

### Phase 5: Standalone CLI Support
- [ ] `CommandProvider` SPI in karate-core for dynamic subcommand discovery
- [ ] `PerfCommand` in karate-gatling (`karate perf`)
- [ ] Dynamic simulation generation from feature files
- [ ] `karate-gatling-bundle.jar` fatjar (Gatling + Scala + karate-gatling)

### Phase 6: Profiling & Validation

Lives in `karate-profiling` behind `-Pgatling`, not here — see
[PROFILING.md](./PROFILING.md) §2 for the workloads and §6 for the first numbers.

- [x] Overhead comparison test (v2 karate-gatling vs plain Gatling) — matched pairs, null and HTTP
- [ ] Port v1's `examples/profiling-test` for memory leak detection
- [ ] Extended load tests (HTTP client pooling, mock server under sustained load) — needs
      `--duration` support in the Gatling workloads, which is now built
- [x] Document profiling methodology and results — first baseline recorded

Two things the first measurement turned up, neither chased down: an empty feature constructs
exceptions on its happy path, and `ApacheHttpClient.initHttpClient` shows up per execution —
i.e. §2.1's `PooledHttpClientFactory` was never built and this is what that costs. The
"< 5% vs plain Gatling" target above is not a meaningful gate as written: Karate allocates
~2.9x plain Gatling for the same requests, because it parses and structurally matches every
response rather than extracting one JSONPath — a ratio between two tools doing different
work. The criterion that answers a load tester's actual question is whether the overhead
disappears into the network time of the system under test; see
[PROFILING.md](./PROFILING.md) §2, "What acceptable overhead should mean here".
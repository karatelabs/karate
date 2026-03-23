# Karate V2 Mock Server

This document describes the mock server for Karate V2.

> See also: [RUNTIME.md](./RUNTIME.md) | [CLI.md](./CLI.md)

---

## Overview

Mock servers in Karate allow you to create API test-doubles using feature files. Scenarios act as request matchers, with JavaScript expressions determining which scenario handles each incoming request.

---

## Architecture

```
CLI / karate.start()
        ↓
    MockServer (HttpServer wrapper)
        ↓
    MockHandler implements Function<HttpRequest, HttpResponse>
        ↓
    ┌───────────────────────────────────────────┐
    │  For each request:                        │
    │  1. Set up request variables              │
    │  2. Evaluate scenarios (first match wins) │
    │  3. Execute matched scenario              │
    │  4. Build response from variables         │
    └───────────────────────────────────────────┘
```

### Core Classes

| Class | Location | Description |
|-------|----------|-------------|
| `MockServer` | `io.karatelabs.core.MockServer` | Public API, wraps HttpServer with mock feature handling |
| `MockHandler` | `io.karatelabs.core.MockHandler` | Request routing and scenario execution |
| `MockConfig` | `io.karatelabs.core.MockConfig` | Global mock configuration (CORS, headers, hooks) |

---

## Feature File Structure

```gherkin
Feature: User API Mock

Background:
  # Executes ONCE on server startup (not per request)
  * def users = {}
  * def uuid = function(){ return java.util.UUID.randomUUID() + '' }
  * configure cors = true
  * configure responseHeaders = { 'Content-Type': 'application/json' }

Scenario: pathMatches('/users/{id}') && methodIs('get')
  * def user = users[pathParams.id]
  * def response = user
  * def responseStatus = user ? 200 : 404

Scenario: pathMatches('/users') && methodIs('post')
  * def id = uuid()
  * users[id] = request
  * users[id].id = id
  * def response = users[id]
  * def responseStatus = 201

Scenario:
  # Catch-all (no matcher expression = always matches)
  * def responseStatus = 404
  * def response = { error: 'not found' }
```

### Mock Feature Structure

- **Background** executes once on server startup
- **Scenarios** are request matchers, not test cases
- **Scenario names** are JavaScript expressions for matching
- **`Scenario Outline`** is NOT supported in mock mode

---

## Request Variables

Available in all mock scenarios:

| Variable | Type | Description |
|----------|------|-------------|
| `request` | Object | Parsed request body (JSON object, XML, or string) |
| `requestBytes` | byte[] | Raw request body bytes |
| `requestPath` | String | URL path without query string (e.g., `/users/123`) |
| `requestUri` | String | Path with query string (e.g., `/users?name=bob`) |
| `requestUrlBase` | String | Base URL (e.g., `http://localhost:8080`) |
| `requestMethod` | String | HTTP method in uppercase (GET, POST, etc.) |
| `requestHeaders` | Map | Request headers as `Map<String, List<String>>` |
| `requestCookies` | Map | Request cookies as `Map<String, { name, value }>` |
| `requestParams` | Map | Query string or form parameters |
| `requestParts` | Map | Multipart request parts (file uploads) |
| `pathParams` | Map | Path parameters extracted by `pathMatches()` |

---

## Response Variables

Set these in scenarios to configure the response:

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `response` | any | null | Response body (auto-serialized to JSON/XML/text) |
| `responseStatus` | int | 200 | HTTP status code |
| `responseHeaders` | Map | {} | Additional response headers |
| `responseDelay` | int | 0 | Delay in milliseconds before sending response |

---

## Matcher Functions

### pathMatches(pattern)

Match URL path with placeholder extraction:

```javascript
pathMatches('/users/{id}')           // Extracts { id: '123' } from /users/123
pathMatches('/v1/cats/{catId}/toys') // Multiple placeholders supported
```

### methodIs(method)

Case-insensitive HTTP method check:

```javascript
methodIs('get')
methodIs('POST')
```

### typeContains(substring)

Check Content-Type header contains substring:

```javascript
typeContains('json')  // Matches application/json, application/ld+json
typeContains('xml')   // Matches text/xml, application/xml
```

### acceptContains(substring)

Check Accept header contains substring:

```javascript
acceptContains('json')
```

### headerContains(name, value)

Check if any value of a header contains substring:

```javascript
headerContains('Authorization', 'Bearer')
```

### paramValue(name)

Get query/form parameter value:

```javascript
paramValue('page')  // Returns string or null
```

### paramExists(name)

Check if parameter exists:

```javascript
paramExists('debug')
```

### bodyPath(expression)

Extract value from request body using JsonPath or XPath:

```javascript
bodyPath('$.user.name')  // JsonPath
bodyPath('/root/item')   // XPath (paths starting with /)
```

---

## Configuration

### CORS

```gherkin
Background:
  * configure cors = true
```

When enabled:
- OPTIONS requests return 200 with CORS headers (no scenario matching)
- All responses include `Access-Control-Allow-Origin: *`

### Response Headers

```gherkin
Background:
  * configure responseHeaders = { 'Content-Type': 'application/json' }
```

Applied to all responses. Scenario-level `responseHeaders` overrides.

### afterScenario

```gherkin
Background:
  * configure afterScenario = function(){ karate.log('request handled') }
```

Executes after each scenario, before response is sent.

---

## Stateful Mocks

Variables defined in `Background` persist across requests:

```gherkin
Background:
  * def counter = { value: 0 }

Scenario: pathMatches('/increment')
  * counter.value = counter.value + 1
  * def response = { count: counter.value }
```

**Thread safety:** The handler synchronizes request processing to prevent concurrent access to shared state.

---

## JavaScript File Handlers

For programmatic control, you can use pure JavaScript files instead of feature files. This is useful for:
- Integration testing with custom server setups
- Embedding mock logic in Java applications
- Dynamic request routing without Gherkin overhead

### JS File Structure

```javascript
// mock.js - evaluated as a block for each request
// Available: request, response, session

session.items = session.items || {};
session.counter = session.counter || 1;

if (request.pathMatches('/items/{id}')) {
    var id = request.pathParams.id;
    if (request.get) {
        var item = session.items[id];
        if (item) {
            response.body = item;
        } else {
            response.status = 404;
            response.body = { error: 'Not found' };
        }
    } else if (request.delete) {
        delete session.items[id];
        response.status = 204;
    }
} else if (request.pathMatches('/items')) {
    if (request.get) {
        response.body = Object.values(session.items);
    } else if (request.post) {
        var id = '' + session.counter++;
        var item = request.body;
        item.id = id;
        session.items[id] = item;
        response.status = 201;
        response.body = item;
    }
} else {
    response.status = 404;
    response.body = { error: 'Unknown path' };
}
```

### Using JS Files with ServerTestHarness

For integration testing:

```java
import io.karatelabs.http.ServerTestHarness;

ServerTestHarness harness = new ServerTestHarness("classpath:mocks");
harness.start();

harness.setHandler(ctx -> {
    ctx.evalFile("classpath:mocks/crud-mock.js");
    return ctx.response();
});

// Make requests
HttpResponse response = harness.post("/items", Map.of("name", "Widget"));
assertEquals(201, response.getStatus());

harness.stop();
```

### Using JS Files with HttpServer Directly

For production use:

```java
import io.karatelabs.common.Resource;
import io.karatelabs.http.HttpServer;
import io.karatelabs.http.Session;
import io.karatelabs.js.Engine;

Session session = Session.inMemory();
String mockJs = Resource.path("classpath:mocks/api.js").getText();

HttpServer server = HttpServer.start(8080, request -> {
    HttpResponse response = new HttpResponse();
    Engine engine = new Engine();
    engine.put("request", request);
    engine.put("response", response);
    engine.put("session", session);
    engine.eval(mockJs);
    return response;
});
```

### Available Objects in JS Files

Same as feature file mocks:

| Object | Description |
|--------|-------------|
| `request` | HTTP request with `get`, `post`, `put`, `delete`, `pathMatches()`, `pathParams`, `body`, `param()` |
| `response` | HTTP response with `status`, `body`, `headers` |
| `session` | Persistent state across requests |

---

## Proxy Mode

Forward requests to a real backend:

```gherkin
Scenario: requestPath.startsWith('/api')
  * def response = karate.proceed('http://real-backend:8080')

Scenario: requestPath.startsWith('/proxy')
  # Use host from incoming request (true HTTP proxy)
  * def response = karate.proceed()
```

`karate.proceed()` forwards the request and returns the response, which can be modified before returning.

---

## Java API

### MockServer

```java
// Start mock server from feature file
MockServer server = MockServer.feature("api.feature")
    .port(8080)
    .ssl(true)
    .arg(Map.of("key", "value"))
    .start();

// Start from inline feature string
MockServer server = MockServer.featureString("""
    Feature: Test Mock
    Scenario: methodIs('get')
      * def response = { hello: 'world' }
    """)
    .port(0)  // Dynamic port
    .start();

// With watch mode (hot-reload on file changes)
MockServer server = MockServer.feature("api.feature")
    .port(8080)
    .watch(true)
    .start();

// Get server info
int port = server.getPort();
String url = server.getUrl();  // http://localhost:8080

// Stop server
server.stopAsync();     // Non-blocking
server.stopAndWait();   // Blocking
```

### karate.start() (from feature files)

```javascript
// Start mock server from test feature
var server = karate.start({ mock: 'api.feature', port: 8443, ssl: true });

// Use the server
var port = server.port;
var url = server.url;

// Stop when done
server.stop();
```

---

## CLI

```bash
# Start mock server
karate mock -m users.feature -p 8080

# Multiple mock files
karate mock -m users.feature -m orders.feature -p 8080

# With SSL
karate mock -m api.feature -p 8443 --ssl

# With custom certificate
karate mock -m api.feature -p 8443 --ssl --cert cert.pem --key key.pem

# With watch mode (hot-reload on file changes)
karate mock -m api.feature -p 8080 -W
```

CLI options:
- `-m, --mock <file>` - Mock feature file (repeatable)
- `-p, --port <port>` - Port to listen on (default: 0 = dynamic)
- `-s, --ssl` - Enable HTTPS
- `-c, --cert <file>` - SSL certificate (PEM)
- `-k, --key <file>` - SSL private key (PEM)
- `-W, --watch` - Enable hot-reload when feature files change
- `--path-prefix <prefix>` - URL path prefix to strip

---

## SSL/TLS Configuration

### Server-Side SSL (Mock Server)

```java
// Auto-generated self-signed certificate
MockServer server = MockServer.feature("api.feature")
    .port(8443)
    .ssl(true)
    .start();

// With custom certificate
MockServer server = MockServer.feature("api.feature")
    .port(8443)
    .ssl(true)
    .certPath("cert.pem")
    .keyPath("key.pem")
    .start();
```

### Client-Side SSL

```gherkin
# Trust all certificates (for self-signed)
* configure ssl = true

# With keystore for mTLS
* configure ssl = { keyStore: 'classpath:client.pfx', keyStorePassword: 'secret', keyStoreType: 'pkcs12' }
```

---

## Testing

### Unit Tests (MockHandlerTest)

In-memory tests without starting HTTP server:

```java
@Test
void testSimpleGetResponse() {
    Feature feature = Feature.read(Resource.text("""
        Feature: Test Mock
        Scenario: pathMatches('/hello')
          * def response = { message: 'world' }
        """));

    MockHandler handler = new MockHandler(feature);
    HttpRequest request = new HttpRequest();
    request.setMethod("GET");
    request.setPath("/hello");

    HttpResponse response = handler.apply(request);

    assertEquals(200, response.getStatus());
    assertTrue(response.getBodyString().contains("world"));
}
```

### Integration Tests (MockServerTest)

Real HTTPS connections with shared server:

```java
@BeforeAll
static void startServer() {
    server = MockServer.featureString("""
        Feature: Integration Test Mock
        Background:
          * def users = {}
        Scenario: pathMatches('/users/{id}')
          * def response = users[pathParams.id]
        """)
        .port(0)
        .ssl(true)
        .start();
}
```

### End-to-End Tests (MockE2eTest)

Running Karate features against a mock server:

```java
@Test
void testPaymentsMockCrud() {
    ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
        Feature: Test Payments
        Scenario: Create and get payment
        * url 'http://localhost:%d'
        * path '/payments'
        * request { amount: 100 }
        * method post
        * status 200
        """.formatted(port));

    assertPassed(sr);
}
```

---

## V1 Compatibility

### Preserved Behavior
- Scenario matching: first match wins
- Background executes once on startup
- Same request/response variables
- Same matcher function signatures
- CORS and configure options

### V2 Improvements
- No "re-hydration" - objects pass directly via JsCallable
- Cleaner thread-local management
- MockServer as simple wrapper over HttpServer
- CLI under `mock` subcommand

### Not Implemented
- WebSocket support
- gRPC/Kafka mocking
- Browser/driver automation

---

## File Structure

```
karate-v2/
├── karate-core/src/main/java/io/karatelabs/core/
│   ├── MockServer.java          # Public API
│   ├── MockHandler.java         # Request routing
│   ├── MockConfig.java          # Configuration
│   ├── SslConfig.java           # SSL configuration
│   ├── SslContextFactory.java   # SSLContext creation
│   └── SslUtils.java             # SSL utilities
├── karate-core/src/main/java/io/karatelabs/cli/
│   └── MockCommand.java         # CLI subcommand
└── karate-core/src/test/java/io/karatelabs/core/mock/
    ├── MockHandlerTest.java     # Unit tests (11 tests)
    ├── MockServerTest.java      # Integration tests (6 tests)
    ├── MockProxyTest.java       # Proxy mode tests (3 tests)
    └── MockE2eTest.java         # End-to-end tests (35+ tests)
```

---

## References

- V1 MockHandler: `/Users/peter/dev/zcode/karate/karate-core/src/main/java/com/intuit/karate/core/MockHandler.java`
- V1 Documentation: `/Users/peter/dev/zcode/karate/karate-netty/README.md`

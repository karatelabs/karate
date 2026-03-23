# karate-core

The core Karate testing framework providing HTTP client/server, HTML templating, and match assertions.

> See also: [Design Principles](../PRINCIPLES.md) | [Roadmap](../ROADMAP.md) | [karate-js](../karate-js)

## Components

### HTTP (`io.karatelabs.io.http`)

Full-featured HTTP client and server implementation:

- **Client** - Apache HttpClient-based HTTP client with request builder
- **Server** - Netty-based HTTP server for mocking and app serving
- **Authentication** - Basic, Bearer, OAuth2 (Client Credentials, Authorization Code with PKCE)
- **Sessions** - In-memory session store with cookie management
- **Security** - CSRF protection, path security, security headers

### Markup (`io.karatelabs.markup`)

HTML templating engine based on Thymeleaf:

- **Karate Dialect** - Custom template processors for dynamic content
- **HTMX Support** - Built-in HTMX dialect for interactive UIs
- **JavaScript Integration** - Templates can use the karate-js engine
- **Resource Resolution** - Flexible template resource loading

### Match (`io.karatelabs.match`)

Powerful assertion library for comparing values:

- JSON matching with wildcards
- XML matching
- Schema validation
- Fuzzy matching operators

### Core Runtime (`io.karatelabs.core`)

- **ScenarioRuntime** - Test scenario execution engine (major port from Karate 1.x)
- **KarateJs** - Bridge to the JavaScript engine

> **Note:** The ScenarioEngine is a significant piece of work being ported from Karate 1.x. It handles step execution, variable scoping, call hierarchies, and parallel execution.

## Usage

```xml
<dependency>
    <groupId>io.karatelabs</groupId>
    <artifactId>karate-core</artifactId>
    <version>2.0.0.RC1</version>
</dependency>
```

## HTTP Server Example

```java
import io.karatelabs.io.http.HttpServer;
import io.karatelabs.io.http.ServerConfig;

ServerConfig config = new ServerConfig();
config.setPort(8080);

HttpServer server = new HttpServer(config);
server.start();
```

## HTML Templating Example

```java
import io.karatelabs.markup.Markup;
import io.karatelabs.markup.MarkupConfig;

MarkupConfig config = new MarkupConfig();
Markup markup = new Markup(config);

Map<String, Object> context = new HashMap<>();
context.put("name", "World");

String html = markup.render("hello.html", context);
```

## Dependencies

- **karate-js** - JavaScript engine for dynamic content
- **Apache HttpClient 5** - HTTP client implementation
- **Netty** - HTTP server implementation
- **Thymeleaf** - HTML template engine
- **SnakeYAML** - YAML parsing
- **FastCSV** - CSV parsing
- **json-path** - JSON path queries

## License

[MIT License](../LICENSE)

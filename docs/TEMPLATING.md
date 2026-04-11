# Karate Templating Guide

Karate's HTML templating engine is based on Thymeleaf syntax but uses **JavaScript expressions** instead of OGNL/SpEL. It supports two modes:

1. **Server Mode** — web applications with HTTP requests, sessions, HTMX, and Alpine.js
2. **Static Mode** — standalone HTML generation (reports, emails, documentation)

## Table of Contents

1. [Design Philosophy](#design-philosophy)
2. [Template Syntax](#template-syntax)
3. [Server-Side JavaScript](#server-side-javascript)
4. [Built-in Objects](#built-in-objects)
5. [HTMX Integration](#htmx-integration)
6. [Alpine.js Data Binding](#alpinejs-data-binding)
7. [Server-Sent Events (SSE)](#server-sent-events-sse)
8. [Common Patterns](#common-patterns)
9. [API Handlers](#api-handlers)
10. [Server Setup](#server-setup)
11. [Static HTML Generation](#static-html-generation)
12. [Using Templates from Karate Features](#using-templates-from-karate-features)
13. [Differences from Standard Thymeleaf](#differences-from-standard-thymeleaf)

---

## Design Philosophy

Karate templating takes a fundamentally different approach from modern JavaScript frameworks:

### No Build Step, No Bundler, No Hydration

Templates are plain `.html` files served directly. No `node_modules`, no webpack, no transpilation. Add Bootstrap, HTMX, and Alpine.js via CDN and start building. The entire stack runs on the JVM.

### JavaScript as Expression Language

Standard Thymeleaf uses OGNL or SpEL — limited expression languages that require learning new syntax. Karate uses JavaScript, giving developers a full programming language for template expressions and server-side logic. Use `items.length` instead of `items.size()`, write real `if/else` blocks, and call functions naturally.

### Server-Side State, Client-Side Reactivity

`<script ka:scope="global">` runs JavaScript on the server to prepare data, handle POST logic, and manage sessions. This replaces backend controllers entirely. HTMX handles server round-trips. Alpine.js handles client-side reactivity. No data fetching layer, no state management library, no API serialization overhead.

### AJAX Fragments as First-Class Citizens

Every template is both a full page and an HTMX fragment. When an HTMX request targets a template, it renders just the fragment. No separate "partial" templates or API endpoints needed for dynamic updates.

### Hot-Reloadable in Dev Mode

Set `devMode: true` and templates reload from the filesystem on every request. No server restart for template changes.

### Production-Proven

This stack runs in production on AWS Lambda (karate-studio SaaS), serves the karate-agent dashboard, and generates Karate's HTML test reports. It handles authentication, session management, CSRF protection, and real-time updates via SSE.

### How It Compares

| | Karate Templating | React/Next.js | Rails/Hotwire | Phoenix LiveView |
|---|---|---|---|---|
| Build step | None | Required | Asset pipeline | Mix |
| Expression language | JavaScript | JSX | ERB/Ruby | HEEx/Elixir |
| Server-side logic | `ka:scope` in template | API routes + React Server Components | Controllers | LiveView modules |
| Client interactivity | Alpine.js + HTMX | React + hooks | Stimulus + Turbo | LiveView JS |
| State management | Server session + Alpine ephemeral | Redux/Zustand/Context | Rails session | LiveView assigns |
| Runtime | JVM | Node.js | Ruby | BEAM |
| Token efficiency (LLM) | High — plain HTML | Low — JSX + hooks + imports | Medium | Medium |

---

## Template Syntax

### Text Content

```html
<span th:text="user.name">Placeholder</span>
<p th:text="'Hello, ' + user.name">Hello, Guest</p>
```

### Conditional Rendering

```html
<div th:if="session.user">Welcome back!</div>
<div th:unless="session.user">Please sign in</div>
<div th:if="items.length > 0">Items found</div>
```

**Truthiness:** `th:if` uses Thymeleaf rules, not JavaScript. Empty string `""` is **truthy** (unlike JS). `"false"`, `"off"`, `"no"` are falsy. For non-empty string checks, use `str.length > 0`.

### Iteration

```html
<tr th:each="item: items">
  <td th:text="item.name">Name</td>
  <td th:text="item.price">Price</td>
</tr>
```

**Iteration status** — `th:each="item: items"` provides:
- `iter.index` (0-based), `iter.count` (1-based)
- `iter.first`, `iter.last`, `iter.even`, `iter.odd`

Explicit syntax: `th:each="item, iter: items"` (the `, iter` is optional — it's always available).

### Template Inclusion

```html
<!-- Simple include (auto-wrapped with ~{}) -->
<div th:replace="header"></div>
<div th:insert="footer"></div>

<!-- Named fragment -->
<div th:replace="~{header :: nav}"></div>

<!-- With parameters -->
<div th:insert="components/badge" th:with="state: session.state, size: 'sm'"></div>
```

- `th:replace` — replaces the host element with the fragment
- `th:insert` — inserts the fragment inside the host element

### Fragment Definition

```html
<!-- In header.html -->
<nav th:fragment="nav">
  <a href="/">Home</a>
  <a href="/about">About</a>
</nav>

<!-- Usage -->
<nav th:replace="~{header :: nav}"></nav>
```

If a file has no `th:fragment`, the entire file is the fragment — reference it without `::`.

### Attribute Manipulation

```html
<!-- Set attributes -->
<div th:attr="'data-id': item.id, 'data-name': item.name">content</div>

<!-- Direct setters -->
<div th:id="'item-' + item.id">...</div>
<input th:value="form.name"/>
<tr th:class="iter.even ? 'even' : 'odd'">...</tr>

<!-- Append/prepend -->
<div foo="x" th:attrappend="foo: 'y'">Result: foo="xy"</div>
```

**Note:** Hyphenated attribute names must be quoted: `th:attr="'data-bs-target': '#modal'"`. Without quotes, hyphens are parsed as subtraction.

### Local Variables

```html
<div th:with="total: price * quantity, label: 'Total: $' + total">
  <span th:text="label">$0</span>
</div>
```

**Note:** `th:with` uses `:` for assignment (JS syntax), not `=` (standard Thymeleaf).

### Karate-Specific Attributes

#### `ka:scope` — Server-Side Script Execution

```html
<script ka:scope="global">
  _.items = db.findItems();
  _.user = session ? session.user : null;
</script>
```

See [Server-Side JavaScript](#server-side-javascript) for details.

#### `ka:set` — Capture Block Content

```html
<pre ka:set="content">
First line
Second line
</pre>
<div th:text="content">...</div>
```

#### `ka:nocache` — Cache Busting (Server Mode Only)

```html
<script src="pub/app.js" ka:nocache="true"></script>
<!-- Renders: <script src="pub/app.js?ts=1699123456789"></script> -->
```

---

## Server-Side JavaScript

### The `_` Variable

Use `_.foo` to **set** variables in `<script ka:scope>` blocks. Access them as `foo` (without `_.`) in template expressions:

```html
<script ka:scope="global">
  _.message = 'Hello World';
  _.items = [{id: 1, name: 'Apple'}, {id: 2, name: 'Banana'}];
</script>

<h1 th:text="message">Message</h1>
<tr th:each="item: items">
  <td th:text="item.name">Name</td>
</tr>
```

Built-in objects (`request`, `session`, `context`) are available directly without `_.`.

### Global vs Local Scope

```html
<!-- Global: runs once, variables available throughout template -->
<script ka:scope="global">
  _.globalData = db.loadData();
</script>

<!-- Local: runs per iteration, variables isolated to that cycle -->
<div th:each="item: items">
  <script ka:scope="local">
    _.markup = item.price * 1.1;
  </script>
  <span th:text="markup">0</span>
</div>
```

### Handling POST Requests

```html
<script ka:scope="global">
  if (request.post) {
    var action = request.param('action');
    if (action == 'create') {
      var form = request.paramJson('form');
      db.save(form);
      context.flash.success = 'Created';
      context.redirect('/items');
    } else if (action == 'delete') {
      db.delete(request.param('id'));
    }
  }
  _.items = db.findAll();
</script>
```

### Java Interop

```html
<script ka:scope="global">
  var SimpleDateFormat = Java.type('java.text.SimpleDateFormat');
  _.formatDate = function(timestamp) {
    return new SimpleDateFormat('yyyy-MM-dd').format(new java.util.Date(parseInt(timestamp) * 1000));
  };
</script>
<span th:text="formatDate(item.createdAt)">2024-01-01</span>
```

---

## Built-in Objects

### `request` — HTTP Request

```javascript
// Method shortcuts (boolean)
request.get, request.post, request.put, request.delete, request.ajax

// Properties
request.method          // "GET", "POST", etc.
request.path            // Path without query string
request.url             // Full URL
request.urlBase         // Base URL (scheme + host)
request.params          // All query/form parameters
request.headers         // All headers
request.body            // Parsed body (JSON object or form data)
request.bodyString      // Raw body as string
request.multiParts      // Multipart form data

// Methods
request.param(name)              // Get single parameter
request.param(name, default)     // With default value
request.paramInt(name)           // Parse as integer
request.paramJson(name)          // Parse JSON field
request.paramValues(name)        // Get all values for parameter
request.header(name)             // Get header value
request.pathMatches(pattern)     // Check path pattern, populates request.pathParams
request.multiPart(name)          // Get multipart field
```

### `context` — Server Context

```javascript
// Properties
context.template        // Current template name
context.sessionId       // Current session ID (or null)
context.flash           // Flash messages object
context.csrf            // CSRF token object

// Methods
context.redirect(path)  // HTTP redirect
context.init()          // Initialize a new session
context.close()         // Destroy session
context.uuid()          // Generate a UUID
context.log(...)        // Log messages
context.read(path)      // Read file as text
context.toJson(obj)     // Serialize to JSON
context.fromJson(str)   // Parse JSON string
context.switch(template) // Switch to different template
```

### `session` — User Session

`null` until `context.init()` is called. Read/write any property:

```javascript
session.user = { id: 123, name: 'John', admin: true };
session.visitCount = (session.visitCount || 0) + 1;
delete session.user; // logout
```

### `response` — HTTP Response

```javascript
response.status = 404;
response.body = { error: 'Not found' };  // auto-serialized to JSON
response.headers['X-Custom'] = 'value';
```

---

## HTMX Integration

Karate provides `ka:` attributes that compile to `hx-` attributes with added value (URL resolution, JS expression evaluation). For pass-through attributes, use `hx-*` directly.

### HTTP Method Attributes

```html
<button ka:get="search" hx-target="#results">Search</button>
<form ka:post="this" hx-target="#main-content">...</form>
<button ka:delete="/api/items" ka:vals="id:item.id">Delete</button>
```

`ka:get/post/put/patch/delete` resolves template names to URLs and prepends `contextPath`. The `"this"` keyword references the current template.

### `ka:vals` — Dynamic Values

Evaluates a JS expression to JSON and wraps in single-quoted `hx-vals`:

```html
<button ka:post="admin-teams" ka:vals="action:'delete',teamId:team.teamId"
        hx-target="#main-content" hx-confirm="Delete this team?">Delete</button>
```

The value is implicitly wrapped as an object — write `ka:vals="key:value"`, not `ka:vals="{key:value}"`.

> **Important:** `ka:vals` on a submit button inside an HTMX form is silently dropped. HTMX includes `hx-vals` only from the element that triggers the request. For forms, use a hidden `<input>` for action parameters:
> ```html
> <form ka:post="this" hx-target="#main-content">
>   <input type="hidden" name="action" value="save"/>
>   <button type="submit">Save</button>
> </form>
> ```

### When to Use `ka:` vs `hx-`

| Attribute | Use `ka:` | Why |
|-----------|-----------|-----|
| `get`, `post`, `put`, `patch`, `delete` | Yes | URL resolution + contextPath |
| `vals` | Yes | JS expression → JSON + single-quote wrapping |
| `data` | Yes | Alpine binding + hidden input injection |
| Everything else | No — use `hx-*` directly | No preprocessing needed |

Common `hx-*` attributes: `hx-target`, `hx-swap`, `hx-trigger`, `hx-confirm`, `hx-push-url`, `hx-select`, `hx-indicator`, `hx-boost`, `hx-include`, `hx-ext`, `hx-disabled-elt`.

### Loading Indicators

```css
.ka-indicator { display: none; }
.htmx-request .ka-indicator { display: inline-block; }
.htmx-request .ka-indicator-hide { display: none; }
```

```html
<button ka:post="save" hx-target="#result">
  <i class="bi bi-check ka-indicator-hide"></i>
  <span class="ka-indicator spinner-border spinner-border-sm"></span>
  Save
</button>
```

HTMX adds `.htmx-request` automatically during requests — no JS needed.

---

## Alpine.js Data Binding

The `ka:data` attribute bridges server-side data to Alpine.js `x-data`.

**Syntax:** `ka:data="alpineVar:serverExpression"`

### On Any Element (Read-Only Binding)

```html
<script ka:scope="local">
  _.userProfile = db.getUser(userId);
</script>
<div ka:data="user:userProfile">
  <h2 x-text="user.name">Name</h2>
  <span x-show="user.verified" class="badge">Verified</span>
</div>
```

Generates: `x-data='{ user: {"name":"John","verified":true} }'` (single-quoted for safe JSON embedding).

### On `<form>` Elements (Bidirectional Binding)

```html
<script ka:scope="local">
  _.initialForm = { email: '', role: 'user', products: [] };
</script>
<form ka:data="form:initialForm" ka:post="manage-team" hx-target="#main-content">
  <input x-model="form.email" type="email"/>
  <select x-model="form.role">
    <option value="user">User</option>
    <option value="admin">Admin</option>
  </select>
  <button type="submit">Save</button>
</form>
```

On `<form>`, `ka:data` also injects: `<input type="hidden" name="form" x-bind:value="JSON.stringify(form)"/>`

**Server reads it back:** `var form = request.paramJson('form');`

---

## Server-Sent Events (SSE)

Karate's HTTP server supports SSE for real-time streaming via `SseHandler` and `SseConnection`.

### Server Setup

```java
SseHandler sseHandler = (request, connection) -> {
    Thread.ofVirtual().start(() -> {
        while (connection.isOpen()) {
            connection.send("status", "{\"active\": 3}");
            Thread.sleep(1000);
        }
    });
};
connection.onDisconnect(() -> cleanup());

HttpServer.start(port, requestHandler, sseHandler);
```

### SseConnection API

| Method | Description |
|--------|-------------|
| `send(event, data)` | Send named event with data |
| `send(data)` | Send unnamed event |
| `sendComment(text)` | Send comment (keep-alive) |
| `close()` | Close connection |
| `isOpen()` | Check if client connected |
| `onDisconnect(callback)` | Register cleanup callback |

Multiline data is handled automatically — newlines split into separate `data:` lines per the SSE spec.

### Client-Side with HTMX SSE Extension

```html
<script src="https://unpkg.com/htmx-ext-sse@2.2.2/sse.js"></script>
<div hx-ext="sse" sse-connect="/sse/updates" sse-swap="status">
  Waiting for updates...
</div>
```

### Client-Side with EventSource

```javascript
var source = new EventSource('/sse/updates');
source.addEventListener('status', function(e) {
    var data = JSON.parse(e.data);
    document.getElementById('progress').style.width = data.progress + '%';
});
```

### Polling Alternative

For simpler cases, use HTMX polling instead of SSE:

```html
<div hx-get="/api/status" hx-trigger="every 3s" hx-swap="innerHTML">Loading...</div>
```

### Limitations

- SSE connections bypass `ServerRequestHandler` — no access to sessions, CSRF tokens, or template context
- SSE is one-directional (server → client)

---

## Common Patterns

### Authentication Flow

```html
<script ka:scope="global">
  if (request.post) {
    var authType = request.param('authType');
    context.init();
    session.state = context.uuid();
    session.authType = authType;
    context.redirect(auth.getOAuthUrl(authType, session.state));
  } else if (request.param('code')) {
    if (request.param('state') !== session.state) {
      throw 'CSRF validation failed';
    }
    session.user = auth.exchangeCode(request.param('code'), session.authType);
    delete session.state;
    context.redirect('/');
  }
</script>

<form method="post" th:action="context.template">
  <button name="authType" value="google">Sign in with Google</button>
  <button name="authType" value="github">Sign in with GitHub</button>
</form>
```

### Protected Routes

```html
<script ka:scope="global">
  if (!session || !session.user) {
    context.redirect('/signin');
  }
</script>
```

### SPA-like Navigation

```html
<!-- header.html -->
<nav hx-target="#main-content">
  <a ka:get="dashboard">Dashboard</a>
  <a ka:get="users">Users</a>
  <a ka:get="settings">Settings</a>
</nav>

<!-- index.html -->
<div th:replace="header"></div>
<div id="main-content">
  <div th:insert="dashboard"></div>
</div>
```

### Flash Messages

```html
<script ka:scope="global">
  if (request.post) {
    db.save(request.paramJson('form'));
    context.flash.success = 'Saved!';
    context.redirect('/items');
  }
</script>

<!-- After redirect — auto-cleared after first read -->
<div th:if="context.flash.success" class="alert alert-success" th:text="context.flash.success"></div>
<div th:if="context.flash.error" class="alert alert-danger" th:text="context.flash.error"></div>
```

### Partial Rendering for AJAX

```html
<div th:unless="request.ajax">
  <head th:replace="~{index :: head}"></head>
  <nav th:replace="header"></nav>
</div>
<div id="content">
  <tr th:each="item: items">
    <td th:text="item.name">Name</td>
  </tr>
</div>
```

### CRUD Table with HTMX

```html
<script ka:scope="global">
  if (request.post) {
    var action = request.param('action');
    if (action == 'delete') db.deleteItem(request.paramInt('id'));
  }
  _.items = db.findAll();
</script>

<table hx-target="#main-content">
  <tr th:each="item: items">
    <td th:text="item.name">Name</td>
    <td>
      <button ka:post="this" ka:vals="action:'delete',id:item.id"
              hx-confirm="'Delete ' + item.name + '?'">Delete</button>
    </td>
  </tr>
</table>
```

---

## API Handlers

Plain `.js` files in the `api/` directory handle REST API requests. Global variables from `ServerConfig` are available.

```javascript
// api/items.js
if (request.get) {
  var id = request.paramInt('id');
  response.body = id ? db.findById(id) : db.findAll();
  if (id && !response.body) response.status = 404;
}

if (request.post) {
  response.body = db.create(request.body);
  response.status = 201;
}

if (request.delete) {
  db.delete(request.paramInt('id'));
  response.body = { deleted: true };
}
```

### Sessions in API Handlers

Sessions are not auto-created for API requests. Use `session || context.init()`:

```javascript
// api/todos.js
session || context.init();
session.todos = session.todos || [];

if (request.post) {
  var todo = request.body;
  todo.id = context.uuid();
  session.todos.push(todo);
  response.body = todo;
  response.status = 201;
} else {
  response.body = session.todos;
}
```

### Sub-Path Routing

`/api/todos/abc` routes to `api/todos.js` with path `/todos/abc`. Use `request.pathMatches()`:

```javascript
if (request.pathMatches('/{resource}/{id}')) {
  var todo = session.todos.find(function(t) { return t.id === request.pathParams.id; });
  response.body = todo || (response.status = 404, { error: 'Not found' });
} else {
  response.body = session.todos;
}
```

---

## Server Setup

### Basic Configuration

```java
ServerConfig config = new ServerConfig()
    .resourceRoot("classpath:web")
    .sessionStore(new InMemorySessionStore())
    .sessionExpirySeconds(3600)
    .devMode(true);

RequestHandler handler = new RequestHandler(config, resolver);
HttpServer server = HttpServer.start(8080, handler);
```

### ServerConfig Options

| Option | Default | Description |
|--------|---------|-------------|
| `resourceRoot(path)` | none | Root path for templates and resources |
| `sessionStore(store)` | null | Session store (null = no sessions) |
| `sessionExpirySeconds(sec)` | 600 | Session timeout |
| `sessionCookieName(name)` | `"karate.sid"` | Session cookie name |
| `apiPrefix(prefix)` | `"/api/"` | URL prefix for `.js` API routes |
| `staticPrefix(prefix)` | `"/pub/"` | URL prefix for static files |
| `devMode(bool)` | false | Hot reload, disable caching |
| `globalVariables(map)` | null | Variables available in all templates and APIs |
| `csrfEnabled(bool)` | true | CSRF protection |
| `securityHeadersEnabled(bool)` | true | Security headers (X-Frame-Options, etc.) |
| `contentSecurityPolicy(csp)` | null | Custom CSP header |
| `hstsEnabled(bool)` | false | HSTS header (production) |

### Routing

| Path Pattern | Handler | Example |
|--------------|---------|---------|
| `/pub/*` | Static file | `/pub/app.js` → `web/pub/app.js` |
| `/api/*` | JavaScript API | `/api/users` → `web/api/users.js` |
| `/*` | HTML template | `/signin` → `web/signin.html` |

### CSRF Protection

Enabled by default. For HTMX apps, add the token once in the layout:

```html
<meta name="csrf-token" th:content="context.csrf.token"/>
<script>
  document.body.addEventListener('htmx:configRequest', function(e) {
    var token = document.querySelector('meta[name="csrf-token"]');
    if (token) e.detail.headers['X-CSRF-Token'] = token.content;
  });
</script>
```

### Security Headers

Automatically added to HTML responses: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, `Referrer-Policy: strict-origin-when-cross-origin`. Session cookies are `HttpOnly`, and `Secure` + `SameSite=Strict` when not in devMode.

### Global Variables (`SimpleObject`)

Inject Java utilities available in all templates and API handlers:

```java
public class AppUtils implements SimpleObject {
    private final MyDatabase db;

    public AppUtils(MyDatabase db) { this.db = db; }

    @Override
    public Object jsGet(String name) {
        return switch (name) {
            case "findUser" -> (JsCallable) (ctx, args) -> db.findUser(args[0].toString());
            case "formatDate" -> (JsCallable) (ctx, args) -> {
                long ts = ((Number) args[0]).longValue();
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date(ts));
            };
            case "appName" -> "My Application";
            default -> null;
        };
    }

    @Override
    public Collection<String> keys() {
        return List.of("findUser", "formatDate", "appName");
    }
}

// Wire it up
ServerConfig config = new ServerConfig()
    .globalVariables(Map.of("utils", new AppUtils(db)));
```

```html
<span th:text="utils.formatDate(item.createdAt)">2024-01-01</span>
<script ka:scope="global">
  _.user = utils.findUser(request.param('userId'));
</script>
```

**Type conversion:** JS `number` → Java `Number`, `string` → `String`, `boolean` → `Boolean`, `null`/`undefined` → `null`, `array` → `List<Object>`, `object` → `Map<String, Object>`.

---

## Static HTML Generation

For generating standalone HTML files without an HTTP context.

```java
Engine engine = new Engine();
MarkupConfig config = new MarkupConfig();
config.setResolver(new ClasspathResourceResolver());
config.setServerMode(false);
config.setDevMode(false);

Markup markup = Markup.init(engine, config);

Map<String, Object> vars = new LinkedHashMap<>();
vars.put("title", "My Report");
vars.put("items", itemList);

String html = markup.processPath("report-template.html", vars);
Files.writeString(outputPath, html);
```

**Key differences from server mode:** `request`, `response`, `session` are not available. `ka:nocache` is ignored. No HTMX processing. Templates render to static HTML strings.

---

## Using Templates from Karate Features

The `doc` keyword renders HTML templates from feature files. All scenario variables are available in the template:

```cucumber
Feature: Generate Report

Scenario: Create user report
  * def users = [{name: 'Alice', role: 'Admin'}, {name: 'Bob', role: 'User'}]
  * def reportTitle = 'User Summary'
  * doc 'user-report.html'
```

Templates resolve relative to the feature file directory. The rendered HTML is embedded in the step result for HTML reports.

---

## Differences from Standard Thymeleaf

### Expression Language

All expressions use JavaScript, not OGNL/SpEL. Use `items.length` not `items.size()`. Use `list.length == 0` not `list.isEmpty()`.

### `th:with` Syntax

Uses `:` for assignment (JS syntax): `th:with="total: price * quantity"` — not `=` as in standard Thymeleaf.

### Iteration Status Variable

Named `iter` (not `iterStat`): `th:each="item, iter: items"`. Properties: `iter.index`, `iter.count`, `iter.first`, `iter.last`, `iter.even`, `iter.odd`.

### Fragment Auto-Wrapping

Simple paths are auto-wrapped: `th:insert="header"` works (equivalent to `th:insert="~{header}"`). Use full `~{}` syntax for named fragments: `~{header :: nav}`, `~{:: localFragment}`.

### Map Iteration

Maps auto-convert to `[{key: ..., value: ...}]` for `th:each`:

```html
<div th:each="entry: someMap">
  <span th:text="entry.key">key</span>: <span th:text="entry.value">value</span>
</div>
```

### Gotchas

1. **`::` in HTMX attributes** — `hx-on::after-request` breaks Thymeleaf parsing. Use `hx-on-htmx-after-request` instead.

2. **Hyphenated `th:attr` names** — Quote them: `th:attr="'data-bs-target': '#modal'"`. Unquoted hyphens are parsed as subtraction.

3. **Colons in ternary expressions** — `:` near `th:` attributes can confuse the parser. Split complex ternaries into `th:if`/`th:unless` pairs.

4. **Thymeleaf parse errors are opaque** — No line numbers. Debug by binary search: comment out half the template, narrow down.

5. **Inline JS variable passing** — Use `context.toJson()` + unescaped `[(${...})]` for JSON. `[[${...}]]` HTML-escapes output, breaking JSON.

---

## Quick Reference

| Feature | Syntax |
|---------|--------|
| Text output | `th:text="expr"` |
| Conditional | `th:if="expr"` / `th:unless="expr"` |
| Loop | `th:each="item: list"` |
| Include | `th:replace="file"` or `th:insert="file"` |
| Fragment params | `th:with="key: value"` |
| Server JS | `<script ka:scope="global">` |
| Set template var | `_.variable = value` |
| Session | `session.key = value` |
| Request data | `request.param('name')` |
| Redirect | `context.redirect('/path')` |
| HTMX request | `ka:get="path"` / `ka:post="path"` |
| HTMX data | `ka:vals="key:value"` |
| Alpine binding | `ka:data="var:expr"` |
| Flash message | `context.flash.success = 'msg'` |
| Global vars | `ServerConfig.globalVariables(map)` |
| Karate doc | `* doc 'template.html'` |

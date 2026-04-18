# Karate Templating — API Reference

> **Scope note (for future editors and LLMs):** This file is a **minimal API reference** for the open-source karate-markup engine. It lists what exists — attributes, built-in objects, configuration options — with one short example each. **Do not expand it.** Design philosophy, patterns, gotchas, anti-patterns, pattern indexes, and "how to build real apps with this stack" guidance are intentionally out of scope — that body of knowledge lives in Karate Labs' commercial documentation and should not be duplicated here.
>
> Rule of thumb: if you're about to add a second example, a "common pattern", or a "best practice" section, stop. Either it's a new capability worth one line in the reference, or it belongs in the commercial skill doc.

## Modes

- **Server mode** — HTTP server, sessions, HTMX, Alpine.js.
- **Static mode** — standalone HTML generation (reports, emails).

Expression language: **JavaScript** (not OGNL/SpEL). Use `items.length`, not `items.size()`.

---

## `th:` Attributes

| Attribute | Purpose | Example |
|---|---|---|
| `th:text` | Set text content (escaped) | `th:text="user.name"` |
| `th:utext` | Set text content (unescaped — trusted HTML only) | `th:utext="renderedHtml"` |
| `th:if` / `th:unless` | Conditional render | `th:if="user.admin"` |
| `th:each` | Loop; status var `iter` has `.index`, `.count`, `.first`, `.last`, `.even`, `.odd` | `th:each="item, iter: items"` |
| `th:attr` | Set arbitrary attribute (quote hyphens) | `th:attr="'data-id': item.id"` |
| `th:class` / `th:classappend` | Set / append class | `th:classappend="active ? 'on' : ''"` |
| `th:id`, `th:value`, `th:action`, `th:title`, etc. | Set that attribute | `th:action="context.template"` |
| `th:with` | Local variables (uses `:`, not `=`) | `th:with="total: price * qty"` |
| `th:insert` | Insert fragment inside host element | `th:insert="header"` |
| `th:replace` | Replace host element with fragment | `th:replace="~{header :: nav}"` |
| `th:fragment` | Define a named fragment | `th:fragment="nav"` |

Simple paths auto-wrap: `th:insert="header"` ≡ `~{header}`. Named fragments use `~{file :: name}`.

Truthiness follows Thymeleaf, not JS: empty string `""` is truthy; `"false"`, `"off"`, `"no"` are falsy. Check non-empty strings with `str.length > 0`.

Map iteration auto-converts to `[{key, value}]` entries.

---

## Karate-Specific Attributes

| Attribute | Purpose |
|---|---|
| `ka:scope="global"` | Run JavaScript on the server; variables set via `_.foo` are available in subsequent expressions. |
| `ka:scope="local"` | Same, scoped to each `th:each` iteration. |
| `ka:set="name"` | Capture block content into a variable. |
| `ka:nocache="true"` | Append `?ts=<epoch-ms>` to `src` / `href` for cache busting (server mode only). |

```html
<script ka:scope="global">
  _.items = db.findItems();
</script>
<tr th:each="item: items">
  <td th:text="item.name">Name</td>
</tr>
```

---

## Server-Side JavaScript

Set template variables with `_.foo = value`; access as `foo` in expressions. Built-in objects (`request`, `session`, `context`, `response`) are available directly.

```html
<script ka:scope="global">
  if (request.post) {
    db.save(request.paramJson('form'));
    context.flash.success = 'Saved';
    context.redirect('/items');
  }
  _.items = db.findAll();
</script>
```

Java interop: `Java.type('java.text.SimpleDateFormat')`.

---

## Built-in Objects

### `request`

Booleans: `get`, `post`, `put`, `delete`, `ajax`.
Properties: `method`, `path`, `url`, `urlBase`, `params`, `headers`, `body`, `bodyString`, `multiParts`, `pathParams`.
Methods: `param(name)`, `param(name, default)`, `paramInt(name)`, `paramJson(name)`, `paramValues(name)`, `header(name)`, `pathMatches(pattern)`, `multiPart(name)`.

### `response`

Properties: `status`, `body` (auto-serialized to JSON for Map/List), `headers`.

### `session`

`null` until `context.init()`. Read/write arbitrary keys. `delete session.user` to remove.

### `context`

Properties: `template`, `sessionId`, `flash`, `csrf`.
Methods: `init()`, `close()`, `redirect(path)`, `switch(template)`, `uuid()`, `log(...)`, `read(path)`, `toJson(obj)`, `fromJson(str)`.

`redirect(path)` and `switch(template)` throw an internal `TemplateFlowSignal` that aborts the current template render. Statements after the call do not run, and the rest of the template does not render. For `redirect`, a 302 is sent; for `switch`, the replacement template is rendered instead.

---

## HTMX Integration

`ka:` attributes compile to `hx-*` with added preprocessing (URL resolution, expression evaluation). For pass-through attributes, use `hx-*` directly.

| Attribute | Purpose |
|---|---|
| `ka:get`, `ka:post`, `ka:put`, `ka:patch`, `ka:delete` | Resolves template name to URL, prepends `contextPath`. `"this"` = current page. |
| `ka:vals` | Evaluates JS expression → JSON + single-quoted `hx-vals`. Write `action:'x',id:item.id` (no braces). |
| `ka:data` | Alpine `x-data` binding; on `<form>` also injects hidden input with `JSON.stringify(var)`. |

All other HTMX attributes (`hx-target`, `hx-swap`, `hx-trigger`, `hx-confirm`, `hx-push-url`, `hx-boost`, `hx-include`, `hx-select`, `hx-indicator`, `hx-ext`, `hx-disabled-elt`, etc.) — use `hx-*` directly.

`ka:vals` on a submit button inside an HTMX form is silently dropped; use a hidden `<input>` for the action parameter.

---

## Alpine.js Data Binding

```html
<script ka:scope="local">
  _.initialForm = { email: '', role: 'user' };
</script>
<form ka:data="form:initialForm" ka:post="save">
  <input x-model="form.email"/>
  <button type="submit">Save</button>
</form>
```

On any element: adds `x-data='{ form: {...} }'` (single-quoted for safe JSON).
On `<form>`: also adds `<input type="hidden" name="form" x-bind:value="JSON.stringify(form)"/>`. Server reads with `request.paramJson('form')`.

---

## Server-Sent Events

```java
SseHandler sseHandler = (request, connection) -> {
    Thread.ofVirtual().start(() -> {
        while (connection.isOpen()) {
            connection.send("status", "{\"active\":3}");
            Thread.sleep(1000);
        }
    });
};
HttpServer.start(port, requestHandler, sseHandler);
```

`SseConnection` methods: `send(event, data)`, `send(data)`, `sendComment(text)`, `close()`, `isOpen()`, `onDisconnect(cb)`.

SSE bypasses `ServerRequestHandler` — no sessions, no CSRF, no template context.

---

## API Handlers

Plain `.js` files in the `api/` directory. Global variables from `ServerConfig` are available.

```javascript
// api/items.js
if (request.get) {
  var id = request.paramInt('id');
  response.body = id ? db.findById(id) : db.findAll();
}
if (request.post) {
  response.body = db.create(request.body);
  response.status = 201;
}
```

Sub-path routing: `/api/todos/abc` → `api/todos.js` with path `/todos/abc`; use `request.pathMatches('/{resource}/{id}')` to extract params.

Sessions are not auto-created for API requests. Use `session || context.init()`.

---

## Server Setup

```java
ServerConfig config = new ServerConfig()
    .resourceRoot("classpath:web")
    .sessionStore(new InMemorySessionStore())
    .devMode(true)
    .globalVariables(Map.of("utils", new AppUtils(db)));

HttpServer.start(8080, new RequestHandler(config, resolver));
```

### `ServerConfig` options

| Option | Default | Purpose |
|---|---|---|
| `resourceRoot(path)` | — | Root for templates and resources |
| `contextPath(path)` | `""` | URL prefix |
| `devMode(bool)` | `false` | Hot reload, disable caching |
| `sessionStore(store)` | `null` | Session store (null = no sessions) |
| `sessionExpirySeconds(sec)` | 600 | Session TTL |
| `sessionCookieName(name)` | `karate.sid` | Session cookie name |
| `apiPrefix(prefix)` | `/api/` | URL prefix for `.js` handlers |
| `staticPrefix(prefix)` | `/pub/` | URL prefix for static files |
| `csrfEnabled(bool)` | `true` | CSRF protection |
| `allowedOrigins(...)` | — | CORS origins |
| `securityHeadersEnabled(bool)` | `true` | X-Frame-Options, X-Content-Type-Options, etc. |
| `contentSecurityPolicy(csp)` | `null` | Custom CSP header |
| `hstsEnabled(bool)` | `false` | HSTS header |
| `errorTemplate404(path)` | — | Custom 404 template |
| `errorTemplate500(path)` | — | Custom 500 template |
| `templateRoute(pattern, template)` | — | Map path pattern to template (`/items/{id}` → `detail.html`) |
| `shellTemplate(path)` | `null` | Layout template wrapping non-HTMX navigations |
| `rawPaths(...)` | — | Paths that opt out of `shellTemplate` |
| `requestInterceptor(consumer)` | — | Pre-processing hook |
| `requestFilter(fn)` | — | Return non-null to short-circuit the request (auth gates) |
| `globalVariables(map)` | — | Variables injected into all templates and API handlers |

### Shell template

When `shellTemplate(path)` is set, the server renders the resolved content template first (its `ka:scope` side effects commit), then wraps the content with the shell for full-page navigations. The shell receives `content` (rendered HTML — use `th:utext`) and `contentTemplate` (resolved path) as variables.

Skipped when: `HX-Request: true` is present, the path is in `rawPaths`, the content redirected via `context.redirect()`, or the resolved template equals the shell. API and static responses are never wrapped.

```java
new ServerConfig(resourceRoot)
    .shellTemplate("layout.html")
    .rawPaths("/signin", "/signout");
```

### Template routing

`/pub/*` → static file. `/api/*` → `.js` handler. Everything else → HTML template, resolved via (in order): registered `templateRoute` patterns, then file lookup (`/foo` → `foo.html`), then directory-index fallback (`/foo` → `foo/index.html`).

### CSRF

Enabled by default. Inject the token once in the shell/layout:

```html
<meta name="csrf-token" th:content="context.csrf.token"/>
<script>
  document.body.addEventListener('htmx:configRequest', function(e) {
    var t = document.querySelector('meta[name="csrf-token"]');
    if (t) e.detail.headers['X-CSRF-Token'] = t.content;
  });
</script>
```

### Global variables via `SimpleObject`

```java
public class AppUtils implements SimpleObject {
    @Override
    public Object jsGet(String name) {
        return switch (name) {
            case "findUser" -> (JsCallable) (ctx, args) -> db.findUser(args[0].toString());
            default -> null;
        };
    }
    @Override
    public Collection<String> keys() { return List.of("findUser"); }
}

config.globalVariables(Map.of("utils", new AppUtils(db)));
```

JS → Java type conversion: `number` → `Number`, `string` → `String`, `boolean` → `Boolean`, `null`/`undefined` → `null`, array → `List<Object>`, object → `Map<String, Object>`.

---

## Static HTML Generation

For standalone HTML (reports, emails) without an HTTP context:

```java
Engine engine = new Engine();
MarkupConfig config = new MarkupConfig();
config.setResolver(new ClasspathResourceResolver());
config.setServerMode(false);
Markup markup = Markup.init(engine, config);

String html = markup.processPath("report.html", Map.of("title", "Q3", "items", list));
```

No `request`, `response`, or `session`. No HTMX processing. `ka:nocache` is ignored.

---

## Using Templates from Karate Features

The `doc` keyword renders a template. All scenario variables are available.

```cucumber
Scenario: User report
  * def users = [{name: 'Alice'}, {name: 'Bob'}]
  * doc 'user-report.html'
```

Templates resolve relative to the feature file directory. Rendered HTML is embedded in the HTML report.

---

## Differences from Standard Thymeleaf

- **Expression language is JavaScript** (not OGNL/SpEL). Use `list.length`, `list[0]`, real `if/else`.
- **`th:with` uses `:`** for assignment, not `=`.
- **Iteration status variable is named `iter`** (not `itemStat`): `th:each="item, iter: items"`.
- **Simple fragment paths auto-wrap** with `~{}`: `th:insert="header"` works. Named fragments still need explicit syntax: `~{header :: nav}`.
- **Maps auto-convert** to `[{key, value}]` for `th:each`.

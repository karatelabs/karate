# Karate Templating Guide

This guide documents Karate's HTML templating system, which is based on Thymeleaf syntax but uses JavaScript expressions instead of OGNL/SpEL. The templating engine supports two modes:

1. **Server Mode** - For web applications with HTTP requests, sessions, HTMX
2. **Static Mode** - For generating standalone HTML files (reports, emails, etc.)

## Table of Contents

1. [Overview](#overview)
2. [Templating Modes](#templating-modes)
3. [Template Syntax](#template-syntax)
4. [Server-Side JavaScript](#server-side-javascript)
5. [Built-in Objects](#built-in-objects)
6. [HTMX Integration](#htmx-integration)
7. [Common Patterns](#common-patterns)
8. [Server Setup](#server-setup)
9. [Static HTML Generation](#static-html-generation)
10. [Differences from Standard Thymeleaf](#differences-from-standard-thymeleaf)

---

## Overview

Karate's templating engine provides:

- **HTML templates** with Thymeleaf-like syntax (`th:text`, `th:if`, `th:each`, etc.)
- **JavaScript expressions** instead of OGNL/SpEL
- **Two rendering modes**: server-side (web apps) and static (file generation)
- **Server-side JavaScript execution** via `<script ka:scope="global">`
- **HTMX integration** for dynamic, partial page updates (server mode)
- **Session management** for user state (server mode)

### File Structure

```
app/
  index.html          # Main entry point
  header.html         # Shared fragment
  signin.html         # Authentication page
  api/
    items.js          # REST API endpoint
    session.js        # Session operations
  pub/
    app.js            # Static JavaScript (served as-is)
    app.css           # Static CSS
```

### Key Differences from Standard Thymeleaf

| Feature | Standard Thymeleaf | Karate Templating |
|---------|-------------------|-------------------|
| Expression Language | OGNL/SpEL (`${...}`) | JavaScript (direct) |
| Expression Syntax | `th:text="${user.name}"` | `th:text="user.name"` |
| Server-side Logic | Spring Controllers | `<script ka:scope="global">` |
| Session Access | Spring Session | `session.*` JavaScript object |

---

## Templating Modes

The templating engine operates in two distinct modes, controlled by `MarkupConfig.setServerMode()`:

### Server Mode (`serverMode = true`)

Used for **web applications** served by Karate's HTTP server. This is the default when using `RequestHandler`.

**Features:**
- Full access to `request`, `response`, `session`, `context` objects
- HTMX/AJAX request detection (`request.ajax`)
- Session management with cookies
- Cache-busting via `ka:nocache` (appends `?ts=timestamp` to URLs)
- `<script ka:scope="global">` for server-side JavaScript
- CSRF protection

**Example setup:**
```java
ServerConfig config = new ServerConfig()
    .resourceRoot("classpath:web")
    .sessionStore(new InMemorySessionStore())
    .devMode(true);
RequestHandler handler = new RequestHandler(config, resolver);
HttpServer server = HttpServer.start(8080, handler);
```

### Static Mode (`serverMode = false`)

Used for **generating standalone HTML files** like reports, emails, or documentation. No HTTP context required.

**Features:**
- Pure template processing with variable substitution
- No `request`, `response`, `session` objects (they will be null/undefined)
- No cache-busting (URLs remain unchanged)
- No HTMX processing
- Works with any data passed via `Map<String, Object>`

**Key differences from Server Mode:**
- `ka:nocache` is ignored (no timestamp appended)
- Built-in objects like `request` and `session` are not available
- Templates render to static HTML strings

**Example setup:**
```java
Engine engine = new Engine();
MarkupConfig config = new MarkupConfig();
config.setResolver(new ClasspathResourceResolver());
config.setServerMode(false);  // Static mode
config.setDevMode(false);     // Disable hot reload

Markup markup = Markup.init(engine, config);

// Render with data
Map<String, Object> vars = new LinkedHashMap<>();
vars.put("title", "My Report");
vars.put("items", itemList);
vars.put("summary", summaryData);

String html = markup.processPath("report-template.html", vars);
Files.writeString(outputPath, html);
```

### Choosing the Right Mode

| Use Case | Mode | Example |
|----------|------|---------|
| Web application | Server | Dashboard, admin panel |
| REST API responses | Server | HTML fragments for HTMX |
| Test reports | Static | JUnit/Karate HTML reports |
| Email templates | Static | Notification emails |
| Documentation | Static | Generated docs |
| PDF generation | Static | Invoice templates |

### Template Compatibility

Templates can be written to work in both modes by checking for object availability:

```html
<!-- Works in both modes -->
<div th:if="session" th:text="'Welcome, ' + session.user.name">Welcome</div>
<div th:unless="session">Guest mode</div>

<!-- Server-mode only features -->
<div th:if="request">
  <span th:if="request.ajax">AJAX request</span>
</div>
```

---

## Template Syntax

### Basic Attributes

#### `th:text` - Text Content

Replaces element content with the evaluated expression.

```html
<span th:text="user.name">Placeholder</span>
<p th:text="'Hello, ' + user.name">Hello, Guest</p>
<div th:text="message">Default message</div>
```

#### `th:if` / `th:unless` - Conditional Rendering

Conditionally includes or excludes elements.

```html
<!-- Show only if condition is true -->
<div th:if="session.user">Welcome back!</div>
<div th:if="request.post">Form was submitted</div>
<div th:if="items.length > 0">Items found</div>

<!-- Show only if condition is false -->
<div th:unless="session.user">Please sign in</div>
<div th:unless="request.ajax">This is a full page request</div>
```

**Truthiness rules:** `th:if` uses Thymeleaf's truthiness (not JavaScript's). The key difference is **empty strings**:

| Value | Thymeleaf `th:if` | JavaScript `if` |
|-------|-------------------|-----------------|
| `""` (empty string) | **truthy** | falsy |
| `"false"`, `"off"`, `"no"` | falsy | truthy |
| `null` / `undefined` | falsy | falsy |
| `0` | falsy | falsy |
| `false` | falsy | falsy |

To check for non-empty strings, use explicit length checks:

```html
<!-- WRONG: th:if treats "" as truthy -->
<div th:if="model">Has model</div>

<!-- CORRECT: explicit length check -->
<div th:if="model.length > 0">Has model</div>

<!-- CORRECT: compute boolean in ka:scope -->
<script ka:scope="global">
  _.hasModel = model.length > 0;
</script>
<div th:if="hasModel">Has model</div>
```

#### `th:each` - Iteration

Iterates over arrays/lists.

```html
<!-- Basic iteration -->
<tr th:each="item: items">
  <td th:text="item.name">Name</td>
  <td th:text="item.price">Price</td>
</tr>

<!-- With iteration status -->
<li th:each="item, iter: items">
  <span th:text="iter.index">0</span>:
  <span th:text="item.name">Name</span>
  <span th:if="iter.first">(first)</span>
  <span th:if="iter.last">(last)</span>
</li>

<!-- Inline array -->
<div th:each="color: ['red', 'green', 'blue']">
  <span th:text="color">color</span>
</div>
```

Iteration status properties:
- `iter.index` - 0-based index
- `iter.count` - 1-based count
- `iter.first` - true if first element
- `iter.last` - true if last element
- `iter.even` / `iter.odd` - alternating rows

#### `th:replace` / `th:insert` - Template Inclusion

Include other templates. Karate auto-wraps simple paths with `~{}`, so you can use either syntax:

```html
<!-- Simple syntax (auto-wrapped with ~{}) -->
<div th:replace="header.html"></div>
<div th:insert="footer.html"></div>

<!-- Fragment with context -->
<div th:insert="this:with-called" th:with="foo: 'bar', msg: message"></div>
```

For advanced fragment selectors (named fragments, CSS selectors), use the full `~{}` syntax:

```html
<!-- Named fragment selector -->
<div th:replace="~{header :: nav}"></div>
<div th:insert="~{components :: button}"></div>

<!-- Same-file fragment -->
<div th:insert="~{:: localFragment}"></div>
```

| Standard Thymeleaf | Karate (simplified) | Notes |
|--------------------|---------------------|-------|
| `th:insert="~{header}"` | `th:insert="header"` | Auto-wrapped |
| `th:replace="~{footer}"` | `th:replace="footer"` | Auto-wrapped |
| `th:replace="~{layout :: content}"` | `th:replace="~{layout :: content}"` | Use full syntax for selectors |

#### `th:fragment` - Fragment Definition

Define reusable fragments.

```html
<!-- In header.html -->
<head th:fragment="head">
  <title>My App</title>
  <script src="pub/app.js"></script>
</head>

<nav th:fragment="nav">
  <a href="/">Home</a>
  <a href="/about">About</a>
</nav>

<!-- Usage -->
<head th:replace="~{header :: head}"></head>
<nav th:replace="~{header :: nav}"></nav>
```

### Attribute Manipulation

#### `th:attr` - Set Attributes

```html
<div th:attr="foo: 'bar', data-id: item.id">content</div>
```

#### `th:attrappend` / `th:attrprepend` - Append/Prepend to Attributes

```html
<div foo="x" th:attrappend="foo: 'y'">Result: foo="xy"</div>
<div foo="x" th:attrprepend="foo: 'y'">Result: foo="yx"</div>
```

#### `th:id`, `th:class`, `th:value`, `th:action`, etc.

Direct attribute setters.

```html
<div th:id="'item-' + item.id">...</div>
<input th:value="form.name"/>
<form th:action="context.template" method="post">...</form>
<tr th:class="iter.even ? 'even' : 'odd'">...</tr>
```

#### `th:with` - Local Variables

Define variables for template scope.

```html
<div th:with="total: price * quantity, formatted: '$' + total">
  <span th:text="formatted">$0</span>
</div>
```

### Karate-Specific Attributes

#### `ka:scope` - Server-Side Script Execution

Execute JavaScript on the server before rendering.

```html
<!-- Global scope: variables accessible throughout template -->
<script ka:scope="global">
  _.items = db.findItems();
  _.user = session.user;
  _.isAdmin = session.user && session.user.admin;
</script>

<!-- Local scope: variables accessible only in parent element -->
<div>
  <script ka:scope="local">
    _.itemTotal = item.price * item.quantity;
  </script>
  <span th:text="itemTotal">0</span>
</div>
```

#### `ka:set` - Capture Block Content

Capture multi-line content into a variable.

```html
<pre ka:set="content">
First line
Second line
Third line
</pre>
<div th:text="content">...</div>
```

#### `ka:nocache` - Cache Busting

Append file modification timestamp to URLs.

```html
<script src="pub/app.js" ka:nocache="true"></script>
<!-- Renders: <script src="pub/app.js?ts=1699123456789"></script> -->

<link rel="stylesheet" href="pub/app.css" ka:nocache="true"/>
```

---

## Server-Side JavaScript

### The Underscore Variable (`_`) - Setting Template Variables

The `_` (underscore) variable is a special object used **only in server-side JavaScript** to set variables that become available in template expressions.

**Key Rule:** Use `_.foo` to **set** variables in `<script ka:scope>` blocks, then access as `foo` (without `_.`) in Thymeleaf expressions.

```html
<script ka:scope="global">
  // Set values using _.foo syntax
  _.message = 'Hello World';
  _.items = [{id: 1, name: 'Apple'}, {id: 2, name: 'Banana'}];
  _.isAuthenticated = session.user != null;
</script>

<!-- Access in template WITHOUT the _. prefix -->
<h1 th:text="message">Message</h1>
<tr th:each="item : items">...</tr>
<div th:if="isAuthenticated">Welcome!</div>
```

### Direct Access to Built-in Objects

Built-in objects like `request`, `context`, and `session` can be accessed directly in Thymeleaf expressions without needing to set them via `_.`:

```html
<!-- Access request properties directly -->
<div th:if="request.get">This is a GET request</div>
<div th:if="request.post">This is a POST request</div>
<div th:if="request.ajax">This is an AJAX request</div>

<!-- Access context methods directly -->
<div th:text="context.template">index.html</div>
<div th:text="context.uuid()">uuid-here</div>
<div th:text="context.sessionId">session-id</div>

<!-- Access session data directly (when session exists) -->
<div th:if="session">Session active</div>
<div th:text="session.user">username</div>
```

### When to Use `_.foo`

Use `_.foo` when you need to:
1. **Compute values** that require JavaScript logic
2. **Transform data** before displaying
3. **Store temporary values** for reuse across the template

```html
<script ka:scope="global">
  // Computed values need _.foo
  _.visitCount = (context.session ? context.session.visitCount : 0) + 1;
  _.formattedDate = new Date().toLocaleDateString();
  _.itemsJson = context.toJson(items);

  // Complex logic
  if (request.post) {
    _.formData = {
      name: request.param('name'),
      email: request.param('email')
    };
  }
</script>

<!-- Then access without _. prefix -->
<span th:text="visitCount">0</span>
<span th:text="formattedDate">Date</span>
```

### Global vs Local Scope

```html
<!-- Global scope: runs once, variables persist for entire template -->
<script ka:scope="global">
  _.globalData = db.loadData();
</script>

<!-- Local scope: runs for each iteration, isolated variables -->
<div th:each="item: items">
  <script ka:scope="local">
    _.computedValue = item.price * 1.1; // 10% markup
  </script>
  <span th:text="computedValue">0</span>
</div>
```

### Handling POST Requests

```html
<script ka:scope="global">
  _.formData = null;
  _.message = null;

  if (request.post) {
    // Get form parameters
    _.formData = {
      name: request.param('name'),
      email: request.param('email'),
      age: request.paramInt('age')
    };

    // Process the form
    db.saveUser(_.formData);
    _.message = 'User saved successfully!';

    // Optional: redirect after processing
    // context.redirect('/users');
  }
</script>

<!-- Access without _. prefix in Thymeleaf expressions -->
<div th:if="message" class="alert" th:text="message"></div>
<form method="post" th:action="context.template">
  <input name="name" th:value="formData?.name"/>
  <input name="email" th:value="formData?.email"/>
  <input name="age" type="number" th:value="formData?.age"/>
  <button type="submit">Save</button>
</form>
```

### Multiple Actions with Switch

```html
<script ka:scope="global">
  if (request.post) {
    switch (request.param('action')) {
      case 'create':
        let form = request.paramJson('form');
        db.create(form);
        break;
      case 'delete':
        let id = request.param('id');
        db.delete(id);
        break;
      case 'update':
        let data = request.paramJson('data');
        db.update(data);
        break;
    }
  }
  _.items = db.findAll();
</script>
```

### Java Interop

```html
<script ka:scope="global">
  // Import Java types
  const JavaDate = Java.type('java.util.Date');
  const SimpleDateFormat = Java.type('java.text.SimpleDateFormat');

  // Use Java classes
  _.toDateString = function(timestamp) {
    const date = new JavaDate(parseInt(timestamp) * 1000);
    const formatter = new SimpleDateFormat('yyyy-MM-dd');
    return formatter.format(date);
  };
</script>

<span th:text="toDateString(item.createdAt)">2024-01-01</span>
```

---

## Built-in Objects

### `context` - Server Context

Core utilities and methods.

```javascript
// Properties
context.template      // Current template name (e.g., "index.html")
context.caller        // Caller template name (for fragments)
context.sessionId     // Current session ID (or null)
context.closed        // True if session was closed
context.flash         // Flash messages object

// Methods
context.redirect(path)     // Redirect to another URL
context.init()             // Initialize a new session
context.close()            // Close/invalidate session
context.uuid()             // Generate a UUID
context.log(...)           // Log messages
context.read(path)         // Read file as text
context.readBytes(path)    // Read file as bytes
context.toJson(obj)        // Serialize object to JSON
context.fromJson(str)      // Parse JSON string
context.switch(template)   // Switch to different template
```

**Examples:**

```html
<script ka:scope="global">
  // Initialize session for new visitors
  // After context.init(), 'session' is immediately available
  if (!session) {
    context.init()
  }

  // Now session can be used directly
  _.visitCount = (session.visitCount || 0) + 1
  session.visitCount = _.visitCount

  // Authentication check (after session is initialized)
  if (!session.user) {
    context.redirect('/signin');
  }

  // Generate unique ID
  _.requestId = context.uuid();

  // Logging
  context.log('User accessed:', context.template, 'User:', session.user?.email);

  // Read external data
  _.config = context.fromJson(context.read('config.json'));

  // JSON serialization for client
  _.userJson = context.toJson(session.user);
</script>

<!-- Display flash messages -->
<div th:if="context.flash.error" class="alert-danger" th:text="context.flash.error"></div>
<div th:if="context.flash.success" class="alert-success" th:text="context.flash.success"></div>
```

### `session` - Session Object

User session state, persisted across requests.

#### Session Initialization

The `session` variable is `null` until explicitly initialized with `context.init()`. After calling `context.init()`, the `session` object is immediately available in the same script block.

```html
<script ka:scope="global">
  // Check if session exists, initialize if not
  if (!session) {
    context.init()
  }

  // session is now available immediately after init()
  session.visitCount = (session.visitCount || 0) + 1
</script>

<!-- session is available in template expressions -->
<span th:text="session.visitCount">0</span>
```

**Key Points:**
- `session` is `null` for new visitors (no session cookie)
- Call `context.init()` to create a new session
- After `init()`, `session` is immediately available in the same script
- A session cookie is automatically set in the response
- Use `context.close()` to invalidate/destroy a session

#### Session Properties

```javascript
// Read/write session properties
session.user = { id: 123, name: 'John' };
session.cart = [];
session.visitCount = (session.visitCount || 0) + 1;

// Delete property
delete session.user;
```

**Common Patterns:**

```html
<script ka:scope="global">
  // Store user after login
  if (loginSuccess) {
    session.user = {
      userId: profile.userId,
      email: profile.email,
      name: profile.name,
      admin: profile.admin
    };
  }

  // Shopping cart
  if (request.post && request.param('action') === 'addToCart') {
    session.cart = session.cart || [];
    session.cart.push({
      productId: request.param('productId'),
      quantity: request.paramInt('quantity')
    });
  }
</script>

<!-- Display session data -->
<div th:if="session.user">
  <span>Welcome, </span>
  <span th:text="session.user.name">User</span>
</div>
```

### `request` - HTTP Request Object

Access request data.

```javascript
// Properties
request.method        // "GET", "POST", etc.
request.url           // Full URL
request.urlBase       // Base URL (e.g., "https://example.com")
request.path          // Path without query (e.g., "/users")
request.pathRaw       // Path with query string
request.params        // All query/form parameters
request.headers       // All headers
request.body          // Parsed body (JSON object or form data)
request.bodyString    // Raw body as string
request.bodyBytes     // Raw body as bytes
request.multiParts    // Multipart form data

// Method shortcuts (boolean)
request.get           // true if GET
request.post          // true if POST
request.put           // true if PUT
request.delete        // true if DELETE
request.ajax          // true if HTMX/AJAX request

// Methods
request.param(name)           // Get single parameter
request.param(name, default)  // With default value
request.paramInt(name)        // Parse as integer
request.paramJson(name)       // Parse JSON field
request.paramValues(name)     // Get all values for parameter
request.header(name)          // Get header value
request.headerValues(name)    // Get all header values
request.pathMatches(pattern)  // Check path pattern
request.multiPart(name)       // Get multipart field
```

**Examples:**

```html
<script ka:scope="global">
  // Get query parameters
  let page = request.paramInt('page') || 1;
  let search = request.param('q', '');

  // Handle different request methods
  if (request.get) {
    _.items = db.search(search, page);
  }

  if (request.post) {
    let formData = {
      name: request.param('name'),
      email: request.param('email')
    };
    db.save(formData);
  }

  // Handle JSON body
  if (request.post && request.header('Content-Type').includes('application/json')) {
    let data = request.body;
    processData(data);
  }

  // Handle file uploads
  if (request.post && request.multiParts) {
    let file = request.multiPart('file');
    if (file) {
      storage.save(file.name, file.bytes);
    }
  }
</script>

<!-- Show different content for AJAX requests -->
<div th:unless="request.ajax">
  <header th:replace="header.html"></header>
  <h1>Page Title</h1>
</div>
<div id="content">
  <!-- This part renders for both full and AJAX requests -->
  <tr th:each="item: _.items">...</tr>
</div>
```

### `response` - HTTP Response Object

Control response output (primarily for API handlers).

```javascript
// Properties
response.status       // HTTP status code (default: 200)
response.body         // Response body (auto-serialized to JSON if object)
response.headers      // Response headers map

// Set in API handler (.js file)
response.status = 201;
response.body = { created: true, id: newId };
response.headers['X-Custom-Header'] = 'value';
```

**API Handler Example (api/items.js):**

```javascript
if (request.get) {
  let id = request.paramInt('id');
  if (id) {
    let item = db.findById(id);
    if (item) {
      response.body = item;
    } else {
      response.status = 404;
      response.body = { error: 'Item not found' };
    }
  } else {
    response.body = db.findAll();
  }
}

if (request.post) {
  let data = request.body;
  let newItem = db.create(data);
  response.status = 201;
  response.body = newItem;
}

if (request.delete) {
  let id = request.paramInt('id');
  db.delete(id);
  response.body = { deleted: true };
}
```

---

## HTMX Integration

Karate provides custom attributes that convert to HTMX attributes with additional features.

### HTTP Method Attributes

```html
<!-- ka:get → hx-get -->
<button ka:get="/api/items">Load Items</button>

<!-- ka:post → hx-post -->
<form ka:post="/api/users">
  <input name="email"/>
  <button type="submit">Create</button>
</form>

<!-- ka:put → hx-put -->
<button ka:put="/api/items" ka:vals="id:item.id">Update</button>

<!-- ka:patch → hx-patch -->
<button ka:patch="/api/items" ka:vals="id:item.id,status:'active'">Activate</button>

<!-- ka:delete → hx-delete -->
<button ka:delete="/api/items" ka:vals="id:item.id">Delete</button>
```

### The `this` Keyword

Use `"this"` to reference the current template path.

```html
<!-- Reload current template -->
<button ka:get="this">Refresh</button>

<!-- Post back to current template -->
<form ka:post="this">
  <input name="action" value="save"/>
  <button type="submit">Save</button>
</form>
```

### Dynamic Values with `ka:vals`

Send additional data with requests.

```html
<!-- Single value -->
<button ka:post="/api/action" ka:vals="id:item.id">Click</button>

<!-- Multiple values -->
<button ka:post="/api/action" ka:vals="id:item.id,action:'delete',confirm:true">Delete</button>

<!-- Expression values -->
<tr th:each="item: items" ka:vals="itemId:item.id,itemName:item.name">
  <td>
    <a href="#" hx-get="/api/details" th:text="item.name">Name</a>
  </td>
</tr>

<!-- With action parameter -->
<button hx-post="manage-team" hx-target="#main-content"
        ka:vals="action:'addUser',userId:user.id">
  Add User
</button>
```

> **Important:** `ka:vals` on a submit button inside a form does NOT work. HTMX only includes
> `hx-vals` (which `ka:vals` compiles to) when the attribute is on the **same element** that triggers
> the request. For forms with `ka:post` / `hx-post`, the form element triggers the request — not the
> button. Use a hidden input instead:
> ```html
> <form ka:post="manage-team" hx-target="#main-content">
>   <input type="hidden" name="action" value="addUser"/>
>   <button type="submit">Add User</button>
> </form>
> ```
> `ka:vals` works correctly on standalone elements (links, buttons) that have their own `ka:post` / `ka:get`.

### Target and Swap

```html
<!-- Target specific element -->
<button ka:get="/api/items" ka:target="#results">Load</button>

<!-- Swap strategies -->
<button ka:get="/partial" ka:swap="innerHTML">Replace inner HTML</button>
<button ka:get="/partial" ka:swap="outerHTML">Replace entire element</button>
<button ka:get="/partial" ka:swap="beforeend">Append to end</button>
<button ka:get="/partial" ka:swap="afterbegin">Insert at beginning</button>
<button ka:get="/partial" ka:swap="delete">Delete target</button>

<!-- Dynamic target from expression -->
<button ka:get="/api/item"
        ka:vals="id:item.id"
        ka:target="'#item-' + item.id"
        ka:swap="outerHTML">
  Refresh
</button>
```

### Other HTMX Attributes

```html
<!-- Trigger events -->
<input ka:get="/search"
       ka:trigger="keyup changed delay:300ms"
       ka:target="#results"/>

<!-- Confirmation dialog -->
<button ka:delete="/api/item"
        ka:vals="id:item.id"
        ka:confirm="'Are you sure you want to delete ' + item.name + '?'">
  Delete
</button>

<!-- Loading indicator -->
<button ka:get="/api/slow-operation" ka:indicator="#spinner">
  <span>Process</span>
  <span id="spinner" class="spinner" style="display:none">Loading...</span>
</button>

<!-- Push URL to history -->
<a ka:get="/page/2" ka:push-url="true">Page 2</a>

<!-- Include additional form fields -->
<button ka:post="/api/submit" ka:include="[name='extra']">Submit</button>

<!-- Synchronization -->
<button ka:post="/api/action" ka:sync="closest form:abort">Submit</button>

<!-- Disable element during request -->
<button ka:post="/api/action" ka:disabled-elt="this">Submit</button>
```

### Loading Indicators

Use CSS classes for loading states.

```html
<!-- Element to show during loading -->
<span class="ka-indicator spinner-border spinner-border-sm"></span>

<!-- Element to hide during loading -->
<i class="bi bi-person ka-indicator-hide"></i>

<!-- Combined example -->
<button ka:post="/api/action" hx-target="#result">
  <i class="bi bi-check ka-indicator-hide"></i>
  <span class="ka-indicator spinner-border spinner-border-sm"></span>
  <span>Submit</span>
</button>
```

Add CSS:

```css
.ka-indicator {
  display: none;
}
.htmx-request .ka-indicator {
  display: inline-block;
}
.htmx-request .ka-indicator-hide {
  display: none;
}
```

### Live Updates — HTMX Polling and SSE

Dashboards and status pages need to update without manual refresh. Two approaches work with Karate's HTTP server:

#### Polling with `hx-trigger="every Ns"`

The simplest approach — HTMX re-fetches an HTML fragment on a timer:

```html
<!-- Server renders a partial HTML fragment at /api/status-fragment -->
<div hx-get="/api/status-fragment" hx-trigger="every 3s" hx-swap="innerHTML">
  Loading...
</div>
```

The server endpoint returns an HTML fragment (not a full page):

```java
// In your request handler
if ("/api/status-fragment".equals(path)) {
    HttpResponse resp = new HttpResponse();
    resp.setBody("<span class='badge bg-success'>3 active</span>");
    return resp;
}
```

Or use a Karate template fragment:

```html
<!-- status-fragment.html -->
<script ka:scope="global">
  _.count = grid.activeCount;
</script>
<span class="badge bg-success" th:text="count + ' active'">0 active</span>
```

```java
// Route to template
if ("/api/status-fragment".equals(path)) {
    return templateHandler.apply(request); // renders status-fragment.html
}
```

#### SSE with Karate's `SseHandler`

For real-time push (no polling delay), use Server-Sent Events. Karate's `HttpServer` has built-in SSE support via `SseHandler`:

```java
SseHandler sseHandler = (request, connection) -> {
    String path = request.getPath();
    if (!path.startsWith("/sse/")) {
        connection.close();
        return;
    }
    // Stream events on a background thread
    Thread.ofVirtual().start(() -> {
        while (connection.isOpen()) {
            connection.send("status", "{\"active\": 3}");
            try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
        }
    });
};

httpServer = HttpServer.start(port, requestHandler, sseHandler);
```

HTMX can consume SSE directly with the `sse` extension:

```html
<script src="https://unpkg.com/htmx-ext-sse@2.2.2/sse.js"></script>

<!-- Connect to SSE endpoint, update element on "status" events -->
<div hx-ext="sse" sse-connect="/sse/dashboard" sse-swap="status">
  Waiting for updates...
</div>
```

Each SSE event named `status` replaces the div's content with the event data. The data can be an HTML fragment rendered server-side.

#### Which to use?

| Approach | Latency | Complexity | Best for |
|----------|---------|------------|----------|
| Polling (`every 3s`) | 0–3s delay | Low — just an endpoint returning HTML | Dashboards, status pages |
| SSE | Real-time | Medium — need `SseHandler` + background thread | Live logs, job progress, notifications |

**Tip:** Start with polling — it's simpler and works with any endpoint. Switch to SSE only when sub-second latency matters (e.g., streaming agent actions in real time).

### `ka:data` - AlpineJS Data Binding

The `ka:data` attribute provides seamless data binding between server-side data and AlpineJS. It works on any element to initialize `x-data`, with special form handling for bidirectional binding.

**Syntax:** `ka:data="alpineVar:serverExpression"`

- `alpineVar` - The AlpineJS variable name to create (e.g., `form`, `data`)
- `serverExpression` - Server-side expression for initial data

**What it does:**

On **any element**:
1. Adds `x-data="{ alpineVar: <json-data> }"` attribute
2. Removes the `ka:data` attribute from output

On **`<form>` elements** (additional):
3. Injects a hidden input with `x-bind:value="JSON.stringify(alpineVar)"` for form submission

**Basic Example:**

```html
<script ka:scope="local">
  _.initialForm = {
    email: '',
    name: '',
    subscribe: true
  };
</script>

<!-- Access the variable directly as 'initialForm' (not '_.initialForm') -->
<form ka:data="form:initialForm" ka:post="/api/users" ka:target="#result">
  <input x-model="form.email" placeholder="Email"/>
  <input x-model="form.name" placeholder="Name"/>
  <label>
    <input type="checkbox" x-model="form.subscribe"/>
    Subscribe to newsletter
  </label>
  <button type="submit">Submit</button>
</form>
```

> **Important:** Variables set as `_.varName` in `ka:scope` blocks become available as `varName` directly in subsequent sibling elements. The `_.` prefix is only used when **setting** variables, not when **accessing** them in `ka:data` expressions.

**Renders as:**

```html
<form x-data="{ form: {&quot;email&quot;:&quot;&quot;,&quot;name&quot;:&quot;&quot;,&quot;subscribe&quot;:true} }"
      hx-post="/api/users" hx-target="#result">
  <input type="hidden" name="form" x-bind:value="JSON.stringify(form)"/>
  <input x-model="form.email" placeholder="Email"/>
  <input x-model="form.name" placeholder="Name"/>
  <label>
    <input type="checkbox" x-model="form.subscribe"/>
    Subscribe to newsletter
  </label>
  <button type="submit">Submit</button>
</form>
```

**Server-side Processing:**

```html
<script ka:scope="global">
  if (request.post) {
    // Parse the JSON form data
    let form = request.paramJson('form');
    // form = { email: 'user@test.com', name: 'John', subscribe: true }
    db.createUser(form);
  }
</script>
```

**Complex Data Structures:**

```html
<script ka:scope="local">
  _.teamForm = {
    email: '',
    role: 'user',
    products: [],      // Array of selected product IDs
    notify: true
  };
</script>

<form ka:data="form:teamForm" ka:post="this" ka:target="#main-content">
  <input x-model="form.email" placeholder="Email"/>

  <select x-model="form.role">
    <option value="user">User</option>
    <option value="admin">Admin</option>
  </select>

  <!-- Checkbox array binding -->
  <div th:each="product: products">
    <label>
      <input type="checkbox"
             th:value="product.id"
             x-model="form.products"/>
      <span th:text="product.name">Product</span>
    </label>
  </div>

  <label>
    <input type="checkbox" x-model="form.notify"/>
    Send notification
  </label>

  <input type="hidden" name="action" value="addUser"/>
  <button type="submit">Add User</button>
</form>
```

**Non-Form Usage (read-only binding):**

Use `ka:data` on any element to initialize Alpine data without form submission:

```html
<script ka:scope="local">
  _.userProfile = db.getUser(session.userId);
</script>

<!-- Access as 'userProfile' directly -->
<div ka:data="user:userProfile" class="profile-card">
  <h2 x-text="user.name">Name</h2>
  <p x-text="user.email">email@example.com</p>
  <span x-show="user.verified" class="badge">Verified</span>
</div>

<!-- Iterate over server data -->
<section ka:data="items:products">
  <template x-for="item in items">
    <div class="product">
      <span x-text="item.name"></span>
      <span x-text="'$' + item.price"></span>
    </div>
  </template>
</section>
```

**Combining with HTMX:**

```html
<form ka:data="form:data"
      ka:post="/api/submit"
      ka:target="#result"
      ka:swap="innerHTML"
      ka:indicator="#spinner">
  <input x-model="form.query" placeholder="Search..."/>
  <button type="submit">
    <span class="ka-indicator-hide">Search</span>
    <span class="ka-indicator">Loading...</span>
  </button>
</form>
```

---

## Server-Sent Events (SSE)

Karate's HTTP server supports Server-Sent Events for real-time streaming from server to browser. SSE is useful for live updates, progress tracking, and event-driven UIs.

### Server Setup

Pass an `SseHandler` when starting the server:

```java
SseHandler sseHandler = (request, connection) -> {
    // Run on a background thread to avoid blocking the Netty worker
    Thread.ofVirtual().start(() -> {
        connection.send("status", "{\"progress\": 0}");
        // ... do work ...
        connection.send("status", "{\"progress\": 100}");
        connection.close();
    });
};

HttpServer.start(8080, handler, sseHandler);
```

### SseConnection API

| Method | Description |
|--------|-------------|
| `send(event, data)` | Send a named event with data |
| `send(data)` | Send data without an event name |
| `sendComment(text)` | Send a comment (keep-alive) |
| `close()` | Close the connection |
| `isOpen()` | Check if the connection is still active |
| `onDisconnect(callback)` | Register a callback for client disconnect |

### Client-Side with HTMX

HTMX has built-in SSE support via the `sse` extension:

```html
<div hx-ext="sse" sse-connect="/sse/updates" sse-swap="message">
  <!-- Content will be replaced when 'message' events arrive -->
</div>
```

### Client-Side with JavaScript

For more control, use the native `EventSource` API:

```html
<script>
var source = new EventSource('/sse/updates');

source.addEventListener('status', function(e) {
    var data = JSON.parse(e.data);
    document.getElementById('progress').style.width = data.progress + '%';
});

source.addEventListener('done', function(e) {
    source.close();
});

source.onerror = function() {
    // EventSource auto-reconnects on error
};
</script>
```

### Multi-line Data

SSE data can span multiple lines. Each line is prefixed with `data:`:

```java
connection.send("update", "line1\nline2\nline3");
// Sends:
// event: update
// data: line1
// data: line2
// data: line3
```

### Reconnection

`EventSource` auto-reconnects on connection loss. Use a `?since=N` parameter to resume from the last received event:

```javascript
var eventCount = 0;
var source = new EventSource('/sse/events?since=' + eventCount);
source.onmessage = function(e) {
    eventCount++;
    // process event
};
```

### Limitations

- SSE connections bypass `ServerRequestHandler`, so they do not have access to sessions, CSRF tokens, or template context. Route SSE at the `HttpServer` level, not through templates.
- SSE is one-directional (server to client). For bidirectional communication, use WebSockets.

---

## Common Patterns

### Authentication Flow

```html
<!-- signin.html -->
<script ka:scope="global">
  if (request.post) {
    let authType = request.param('authType');
    context.init();
    session.state = context.uuid();
    session.authType = authType;
    let redirectUrl = auth.getOAuthUrl(authType, session.state);
    context.redirect(redirectUrl);
  } else if (request.param('code')) {
    // OAuth callback
    let code = request.param('code');
    let state = request.param('state');
    if (state !== session.state) {
      throw 'CSRF validation failed';
    }
    let profile = auth.exchangeCode(code, session.authType);
    session.user = profile;
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
<!-- index.html -->
<script ka:scope="global">
  if (!session.user) {
    context.redirect('/signin');
  }
</script>
<body th:if="session.user">
  <!-- Protected content -->
</body>
```

### Conditional Routing

```html
<script ka:scope="global">
  if (session.user) {
    const hasProduct = session.user.products.find(p => p.active);
    if (hasProduct) {
      session.route = 'dashboard';
    } else {
      session.route = 'checkout';
    }
  }
</script>

<div id="main-content">
  <div th:if="session.route=='dashboard'" th:insert="~{dashboard}"></div>
  <div th:if="session.route=='checkout'" th:insert="~{checkout}"></div>
  <div th:unless="session.route" th:insert="~{welcome}"></div>
</div>
```

### SPA-like Navigation with HTMX

```html
<!-- header.html -->
<nav hx-target="#main-content">
  <a href="#" ka:get="dashboard">Dashboard</a>
  <a href="#" ka:get="users">Users</a>
  <a href="#" ka:get="settings">Settings</a>
  <span class="ka-indicator spinner-border"></span>
</nav>

<!-- index.html -->
<div th:replace="header.html"></div>
<div id="main-content">
  <div th:insert="~{dashboard}"></div>
</div>
```

### Form with JSON Data (AlpineJS Integration)

**Recommended: Using `ka:data`**

The `ka:data` attribute provides a clean, declarative way to bind server-side data to AlpineJS forms:

```html
<script ka:scope="global">
  _.defaultForm = { name: '', items: [] };
</script>

<form ka:data="form:_.defaultForm" ka:post="handler" ka:target="#result">
  <input x-model="form.name" placeholder="Name"/>

  <div th:each="opt: options">
    <label>
      <input type="checkbox" th:value="opt.id" x-model="form.items"/>
      <span th:text="opt.name">Option</span>
    </label>
  </div>

  <button type="submit" ka:vals="action:'save'">Save</button>
</form>
```

**Legacy: Hidden Textarea Pattern**

For more control or custom serialization, use the textarea pattern:

```html
<form x-data="{ form: { name: '', items: [] } }">
  <!-- Hidden textarea for JSON serialization -->
  <textarea style="display:none"
            th:text="context.toJson(_.defaultForm)"
            x-init="form = kjs.fromJson($el.value)"
            x-text="kjs.toJson(form)"
            name="form"></textarea>

  <input x-model="form.name" placeholder="Name"/>

  <div x-data="{ options: [{id: 1, name: 'A'}, {id: 2, name: 'B'}] }">
    <template x-for="opt in options">
      <label>
        <input type="checkbox" x-bind:value="opt.id" x-model="form.items"/>
        <span x-text="opt.name"></span>
      </label>
    </template>
  </div>

  <button hx-post="handler" hx-target="#result" ka:vals="action:'save'">
    Save
  </button>
</form>
```

**Server-side processing (same for both patterns):**

```html
<script ka:scope="global">
  if (request.post && request.param('action') === 'save') {
    let form = request.paramJson('form');
    // form = { name: 'Value', items: [1, 2] }
    db.save(form);
  }
</script>
```

### Flash Messages

Flash messages are one-time notifications that survive redirects. They're stored in the session and automatically cleared after being displayed.

**Setting flash messages (before redirect):**

```html
<!-- create-item.html -->
<script ka:scope="global">
  if (request.post) {
    try {
      let item = db.createItem(request.paramJson('form'));
      context.flash.success = 'Item "' + item.name + '" created successfully!';
      context.flash.itemId = item.id;
      context.redirect('/items');  // Flash survives this redirect
    } catch (e) {
      context.flash.error = e.message;
      // Stay on same page to show error
    }
  }
</script>

<!-- Show error if staying on same page -->
<div th:if="context.flash.error" class="alert alert-danger" th:text="context.flash.error"></div>

<form method="post" th:action="context.template">
  <!-- form fields -->
</form>
```

**Displaying flash messages (after redirect):**

```html
<!-- items.html -->
<script ka:scope="global">
  _.items = db.findAllItems();
</script>

<!-- Flash messages from previous request -->
<div th:if="context.flash.success" class="alert alert-success" th:text="context.flash.success"></div>
<div th:if="context.flash.error" class="alert alert-danger" th:text="context.flash.error"></div>

<!-- Use flash data -->
<div th:if="context.flash.itemId">
  <a th:href="'/items/' + context.flash.itemId">View created item</a>
</div>

<!-- Page content -->
<table>
  <tr th:each="item : items">...</tr>
</table>
```

**Key behaviors:**
- Flash messages are stored in the session when `context.redirect()` is called
- They're automatically loaded and cleared on the next request
- Messages only display once (cleared after loading)
- Requires sessions to be enabled (`sessionStore` in ServerConfig)
- Access via `context.flash.key` in `ka:scope` scripts and Thymeleaf expressions

### Partial Rendering for AJAX

```html
<script ka:scope="global">
  _.items = db.findItems();
</script>

<!-- Only render full page structure for non-AJAX requests -->
<!-- Access request.ajax directly - no need to set via _.foo -->
<div th:unless="request.ajax">
  <head th:replace="~{index::head}"></head>
  <nav th:replace="header.html"></nav>
  <h1>Items</h1>
</div>

<!-- This part always renders -->
<div id="items-container">
  <table>
    <thead th:unless="request.ajax">
      <tr><th>Name</th><th>Price</th></tr>
    </thead>
    <tbody>
      <!-- Access items without _. prefix -->
      <tr th:each="item : items" th:id="'item-' + item.id">
        <td th:text="item.name">Name</td>
        <td th:text="item.price">Price</td>
      </tr>
    </tbody>
  </table>
</div>
```

### CRUD Table with HTMX

```html
<script ka:scope="global">
  if (request.post) {
    let action = request.param('action');
    let id = request.paramInt('id');
    switch (action) {
      case 'delete':
        db.deleteItem(id);
        break;
      case 'update':
        db.updateItem(id, request.paramJson('data'));
        break;
    }
  }
  _.items = db.findAllItems();
</script>

<table hx-target="#main-content">
  <tr th:each="item: _.items" th:id="'row-' + item.id" ka:vals="id:item.id">
    <td th:text="item.name">Name</td>
    <td th:text="item.status">Status</td>
    <td>
      <button class="btn btn-sm btn-primary"
              hx-get="edit-item"
              hx-target="'#row-' + item.id"
              ka:vals="id:item.id">
        Edit
      </button>
      <button class="btn btn-sm btn-danger"
              hx-post="this"
              ka:vals="action:'delete',id:item.id"
              ka:confirm="'Delete ' + item.name + '?'">
        Delete
      </button>
    </td>
  </tr>
</table>
```

---

## API Handlers

Plain JavaScript files in the `api/` directory handle REST API requests.

### Basic API Handler (api/items.js)

```javascript
// GET /api/items - List all or get by ID
if (request.get) {
  let id = request.paramInt('id');
  if (id) {
    let item = db.findById(id);
    if (item) {
      response.body = item;
    } else {
      response.status = 404;
      response.body = { error: 'Not found' };
    }
  } else {
    response.body = db.findAll();
  }
}

// POST /api/items - Create
if (request.post) {
  let data = request.body;
  let item = db.create(data);
  response.status = 201;
  response.body = item;
}

// PUT /api/items?id=1 - Update
if (request.put) {
  let id = request.paramInt('id');
  let data = request.body;
  let item = db.update(id, data);
  response.body = item;
}

// DELETE /api/items?id=1 - Delete
if (request.delete) {
  let id = request.paramInt('id');
  db.delete(id);
  response.body = { deleted: true };
}
```

### Session in API Handlers

Sessions are **not** auto-created for API requests. The `session` variable is `null` until explicitly initialized. Use the `session || context.init()` pattern at the top of your API handler:

```javascript
// api/todos.js - CRUD with session-backed storage
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

A session cookie is automatically set in the response after `context.init()`.

### Sub-Path Routing

API requests with sub-paths are routed to the parent handler. For example, `/api/todos/abc` resolves to `api/todos.js`, with the request path set to `/todos/abc`. Use `request.pathMatches()` to extract path parameters:

```javascript
// api/todos.js - handles /api/todos AND /api/todos/{id}
session || context.init();
session.todos = session.todos || [];

if (request.pathMatches('/{resource}/{id}')) {
  var id = request.pathParams.id;
  var todo = session.todos.find(function(t) { return t.id === id; });
  if (todo) {
    response.body = todo;
  } else {
    response.status = 404;
  }
} else if (request.post) {
  var todo = request.body;
  todo.id = context.uuid();
  session.todos.push(todo);
  response.body = todo;
  response.status = 201;
} else {
  response.body = session.todos;
}
```

### Session-Aware API (api/user.js)

```javascript
session || context.init();

// Check authentication
if (!session.user) {
  response.status = 401;
  response.body = { error: 'Unauthorized' };
} else {
  response.body = {
    user: {
      id: session.user.userId,
      name: session.user.name,
      email: session.user.email
    },
    subscriptions: session.user.subscriptions
  };
}
```

---

## Server Setup

This section covers how to configure and start the Karate HTTP server for serving templates and APIs.

### Basic Server Setup

```java
import io.karatelabs.http.*;
import io.karatelabs.markup.*;
import io.karatelabs.markup.HxDialect;
import io.karatelabs.js.Engine;

// 1. Create resource resolver (classpath or file-based)
ResourceResolver resolver = (path, caller) -> Resource.path("classpath:web/" + path);

// 2. Configure the server
ServerConfig config = new ServerConfig()
    .resourceRoot("classpath:web")       // Root for templates/resources
    .sessionStore(new InMemorySessionStore())  // Enable sessions
    .sessionExpirySeconds(3600)          // 1 hour session timeout
    .apiPrefix("/api/")                  // API routes prefix
    .staticPrefix("/pub/")               // Static file routes prefix
    .devMode(true);                      // Hot reload templates (dev only)

// 3. Create request handler
RequestHandler handler = new RequestHandler(config, resolver);

// 4. Start the server
HttpServer server = HttpServer.start(8080, handler);
```

### ServerConfig Options

| Option | Default | Description |
|--------|---------|-------------|
| `resourceRoot(path)` | none | Root path for templates and resources |
| `sessionStore(store)` | null | Session store (null = no sessions) |
| `sessionExpirySeconds(sec)` | 600 | Session timeout in seconds |
| `sessionCookieName(name)` | `"karate.sid"` | Name of the session cookie |
| `apiPrefix(prefix)` | `"/api/"` | URL prefix for API routes |
| `staticPrefix(prefix)` | `"/pub/"` | URL prefix for static files |
| `devMode(bool)` | false | Enable hot reload and disable caching |
| `globalVariables(map)` | null | Global variables for templates/APIs (see below) |
| `csrfEnabled(bool)` | true | Enable CSRF protection |
| `securityHeadersEnabled(bool)` | true | Add security headers to responses |
| `contentSecurityPolicy(csp)` | null | Custom CSP header value |
| `hstsEnabled(bool)` | false | Enable HSTS header (production only) |

### Routing

Requests are routed based on path:

| Path Pattern | Handler | Example |
|--------------|---------|---------|
| `/pub/*` | Static file | `/pub/app.js` → `web/pub/app.js` |
| `/api/*` | JavaScript API | `/api/users` → `web/api/users.js` |
| `/*` | HTML template | `/signin` → `web/signin.html` |

### Security Features

#### CSRF Protection (enabled by default)

CSRF tokens are automatically generated and validated on POST/PUT/PATCH/DELETE requests.

**In templates:**
```html
<form method="post" th:action="context.template">
    <input type="hidden" name="_csrf" th:value="csrf.token">
    <!-- form fields -->
    <button type="submit">Submit</button>
</form>
```

**For HTMX/AJAX requests:**
```html
<meta name="csrf-token" th:content="csrf.token">
<script>
    // Add CSRF token to all HTMX requests
    document.body.addEventListener('htmx:configRequest', (e) => {
        e.detail.headers['X-CSRF-Token'] =
            document.querySelector('meta[name="csrf-token"]').content;
    });
</script>
```

#### Security Headers

These headers are automatically added to HTML responses:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY` (configurable)
- `X-XSS-Protection: 1; mode=block`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Content-Security-Policy` (if configured)
- `Strict-Transport-Security` (if HSTS enabled, production only)

#### Secure Cookies

Session cookies are created with:
- `HttpOnly` - Always set
- `Secure` - Set when not in devMode
- `SameSite=Strict` - Set when not in devMode

### Custom Java Objects

Inject custom Java objects into templates via the external bridge:

```java
// Create a utilities class
public class AppUtils {
    public String formatDate(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(timestamp));
    }
    public List<Price> getPrices() {
        return priceService.getAll();
    }
}

// Configure the engine with external bridge
Engine engine = new Engine();
engine.setExternalBridge((name, args) -> {
    if ("utils".equals(name)) {
        return new AppUtils();
    }
    return null;
});

// Use in templates
<span th:text="utils.formatDate(item.createdAt)">2024-01-01</span>
```

### Global Variables with SimpleObject

For web applications, the recommended way to inject Java utilities is via `ServerConfig.globalVariables()`. This makes objects available in all templates and API handlers without manual wiring.

#### Basic Setup

```java
import io.karatelabs.http.*;
import io.karatelabs.js.JsCallable;
import io.karatelabs.js.SimpleObject;
import java.util.*;

// 1. Create utility class implementing SimpleObject
public class AppUtils implements SimpleObject {

    private final MyDatabase db;
    private final MyAuthService auth;

    public AppUtils(MyDatabase db, MyAuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @Override
    public Object jsGet(String name) {
        return switch (name) {
            case "formatDate" -> (JsCallable) (ctx, args) -> {
                // Args are untyped - manual validation required
                if (args.length == 0 || args[0] == null) {
                    return "";
                }
                long timestamp = ((Number) args[0]).longValue();
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date(timestamp));
            };
            case "formatPrice" -> (JsCallable) (ctx, args) -> {
                if (args.length == 0 || !(args[0] instanceof Number)) {
                    return "$0.00";
                }
                return String.format("$%.2f", ((Number) args[0]).doubleValue());
            };
            case "findUser" -> (JsCallable) (ctx, args) -> {
                if (args.length == 0) return null;
                String id = args[0].toString();
                return db.findUser(id);  // Returns Map or domain object
            };
            case "authenticate" -> (JsCallable) (ctx, args) -> {
                if (args.length < 2) return Map.of("error", "Missing credentials");
                String email = args[0].toString();
                String password = args[1].toString();
                return auth.login(email, password);
            };
            // Non-callable properties
            case "appName" -> "My Application";
            case "appVersion" -> "2.0.0";
            default -> null;
        };
    }

    @Override
    public Collection<String> keys() {
        return List.of("formatDate", "formatPrice", "findUser",
                       "authenticate", "appName", "appVersion");
    }
}

// 2. Configure server with globalVariables
MyDatabase db = new MyDatabase(connectionPool);
MyAuthService auth = new MyAuthService(db);
AppUtils utils = new AppUtils(db, auth);

ServerConfig config = new ServerConfig()
    .resourceRoot("classpath:web")
    .sessionStore(new InMemorySessionStore())
    .globalVariables(Map.of(
        "utils", utils,
        "config", Map.of("maxItems", 100, "debug", false)
    ));

RequestHandler handler = new RequestHandler(config, resolver);
HttpServer server = HttpServer.start(8080, handler);
```

#### Usage in Templates

Global variables are available directly in Thymeleaf expressions and `ka:scope` scripts:

```html
<!-- Direct property access -->
<h1 th:text="utils.appName">App Name</h1>
<span th:text="utils.appVersion">1.0</span>

<!-- Method calls in expressions -->
<span th:text="utils.formatDate(item.createdAt)">2024-01-01</span>
<span th:text="utils.formatPrice(product.price)">$0.00</span>

<!-- In ka:scope scripts -->
<script ka:scope="global">
  _.user = utils.findUser(request.param('userId'));
  _.formattedDate = utils.formatDate(_.user.createdAt);
</script>

<div th:if="user">
  <h2 th:text="user.name">Name</h2>
  <p th:text="formattedDate">Date</p>
</div>
```

#### Usage in API Handlers

Global variables are also available in `.js` API files:

```javascript
// api/users.js
if (request.get) {
    let userId = request.param('id');
    let user = utils.findUser(userId);
    if (user) {
        response.body = {
            ...user,
            formattedDate: utils.formatDate(user.createdAt)
        };
    } else {
        response.status = 404;
        response.body = { error: 'User not found' };
    }
}

if (request.post) {
    let result = utils.authenticate(
        request.param('email'),
        request.param('password')
    );
    if (result.user) {
        session.user = result.user;
        response.body = { success: true };
    } else {
        response.status = 401;
        response.body = { error: result.error };
    }
}
```

#### JsCallable Limitations

**Important:** The `JsCallable` approach lacks compile-time type safety. Each method must:

1. **Validate argument count** - Check `args.length` before accessing
2. **Handle null arguments** - JavaScript may pass `null` or `undefined`
3. **Cast arguments manually** - Numbers come as `Number`, strings as `String`, objects as `Map`
4. **Return compatible types** - Return `Map`, `List`, primitives, or other `SimpleObject` instances

```java
// Example with full validation
case "createOrder" -> (JsCallable) (ctx, args) -> {
    // Validate argument count
    if (args.length < 2) {
        throw new RuntimeException("createOrder requires (userId, items)");
    }

    // Validate and cast first argument
    if (args[0] == null) {
        throw new RuntimeException("userId cannot be null");
    }
    String userId = args[0].toString();

    // Validate and cast second argument (expected: List of Maps)
    if (!(args[1] instanceof List)) {
        throw new RuntimeException("items must be an array");
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) args[1];

    // Validate item structure
    for (Map<String, Object> item : items) {
        if (!item.containsKey("productId") || !item.containsKey("quantity")) {
            throw new RuntimeException("Each item must have productId and quantity");
        }
    }

    // Proceed with business logic
    return orderService.create(userId, items);
};
```

#### Type Conversion Reference

| JavaScript Type | Java Type Received |
|-----------------|-------------------|
| `number` | `Number` (Integer, Long, or Double) |
| `string` | `String` |
| `boolean` | `Boolean` |
| `null` / `undefined` | `null` |
| `array` | `List<Object>` |
| `object` | `Map<String, Object>` |
| Date | `Number` (milliseconds since epoch) |

#### Best Practices

1. **Create a single utils object** - Consolidate related functionality into one `SimpleObject`
2. **Inject dependencies via constructor** - Pass database, services, etc. to the utils class
3. **Return Maps for complex data** - JavaScript/templates work well with `Map<String, Object>`
4. **Log errors, don't swallow them** - Failed operations should be traceable
5. **Keep methods focused** - Each JsCallable should do one thing well

### Programmatic Template Rendering

Render templates outside of HTTP requests:

```java
Engine engine = new Engine();
MarkupConfig markupConfig = new MarkupConfig();
markupConfig.setResolver(resolver);

Markup markup = Markup.init(engine, markupConfig, new HxDialect(markupConfig));

// Render with variables
Map<String, Object> vars = Map.of("name", "John", "items", itemList);
String html = markup.processPath("email-template.html", vars);
```

---

## Static HTML Generation

This section covers generating standalone HTML files (reports, emails, documentation) using static mode.

### Basic Setup

```java
// 1. Create JavaScript engine
Engine engine = new Engine();

// 2. Configure for static mode
MarkupConfig config = new MarkupConfig();
config.setResolver(new ClasspathResourceResolver());
config.setServerMode(false);  // No HTTP context
config.setDevMode(false);     // No hot reload needed

// 3. Initialize markup processor
Markup markup = Markup.init(engine, config);
```

### Resource Resolver

The resolver tells the engine where to find templates:

```java
// Classpath resolver (for bundled templates)
class ClasspathResourceResolver implements ResourceResolver {
    private static final String ROOT = "io/myapp/templates/";

    @Override
    public Resource resolve(String path, Resource caller) {
        return Resource.path("classpath:" + ROOT + path);
    }
}

// File system resolver (for external templates)
class FileResourceResolver implements ResourceResolver {
    private final Path templateDir;

    FileResourceResolver(Path templateDir) {
        this.templateDir = templateDir;
    }

    @Override
    public Resource resolve(String path, Resource caller) {
        return Resource.from(templateDir.resolve(path));
    }
}
```

### Real-World Example: HTML Report Generator

This example shows generating test reports (similar to Karate's HTML reports):

**Template (`report-summary.html`):**

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <link rel="stylesheet" href="res/bootstrap.min.css"/>
    <title>Test Report</title>
</head>
<body>
    <h1>Test Report</h1>
    <p th:text="reportDate">Date</p>

    <!-- Summary cards -->
    <div class="row">
        <div class="card">
            <h5>Features</h5>
            <h2 th:text="summary.featureCount">0</h2>
        </div>
        <div class="card">
            <h5>Passed</h5>
            <h2 th:text="summary.passedCount">0</h2>
        </div>
        <div class="card">
            <h5>Failed</h5>
            <h2 th:text="summary.failedCount">0</h2>
        </div>
    </div>

    <!-- Feature table -->
    <table>
        <thead>
            <tr><th>Feature</th><th>Status</th><th>Duration</th></tr>
        </thead>
        <tbody>
            <tr th:each="f : features">
                <td>
                    <a th:href="'features/' + f.fileName + '.html'"
                       th:text="f.name">Feature</a>
                </td>
                <td>
                    <span th:if="f.passed" class="badge bg-success">PASSED</span>
                    <span th:if="f.failed" class="badge bg-danger">FAILED</span>
                </td>
                <td th:text="f.durationMillis + ' ms'">0 ms</td>
            </tr>
        </tbody>
    </table>

    <footer>
        <small>Generated by <span th:text="appName">App</span></small>
    </footer>
</body>
</html>
```

**Java code:**

```java
public class ReportGenerator {

    private final Markup markup;

    public ReportGenerator() {
        Engine engine = new Engine();
        MarkupConfig config = new MarkupConfig();
        config.setResolver(new ClasspathResourceResolver());
        config.setServerMode(false);
        this.markup = Markup.init(engine, config);
    }

    public void generate(TestResult result, Path outputDir) throws IOException {
        // Create output directories
        Files.createDirectories(outputDir.resolve("features"));
        Files.createDirectories(outputDir.resolve("res"));

        // Copy static resources
        copyResources(outputDir.resolve("res"));

        // Prepare template variables
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("reportDate", formatDate(result.getStartTime()));
        vars.put("appName", "My Test Framework");
        vars.put("summary", Map.of(
            "featureCount", result.getFeatureCount(),
            "passedCount", result.getPassedCount(),
            "failedCount", result.getFailedCount()
        ));
        vars.put("features", buildFeatureList(result));

        // Render and write
        String html = markup.processPath("report-summary.html", vars);
        Files.writeString(outputDir.resolve("report-summary.html"), html);

        // Generate individual feature pages
        for (FeatureResult fr : result.getFeatures()) {
            generateFeaturePage(fr, outputDir.resolve("features"));
        }
    }

    private List<Map<String, Object>> buildFeatureList(TestResult result) {
        // Convert to list of maps for template iteration
        List<Map<String, Object>> features = new ArrayList<>();
        for (FeatureResult fr : result.getFeatures()) {
            features.add(Map.of(
                "name", fr.getName(),
                "fileName", sanitizeFileName(fr.getName()),
                "passed", fr.isPassed(),
                "failed", fr.isFailed(),
                "durationMillis", fr.getDurationMillis()
            ));
        }
        return features;
    }
}
```

### Tips for Static Mode Templates

1. **No server objects**: `request`, `response`, `session` are not available
2. **Use `.length` not `.size()`**: JavaScript arrays use `.length`
3. **Convert Maps to Lists**: For `th:each` iteration, convert Java Maps to Lists of objects
4. **Relative paths**: Use relative paths for resources (`res/style.css`, `../res/style.css`)
5. **No `ka:nocache`**: Cache-busting is ignored in static mode

### Testing Templates

Create a test to quickly regenerate reports during development:

```java
@Test
void testReportGeneration() {
    TestResult result = runTests();

    ReportGenerator generator = new ReportGenerator();
    Path outputDir = Path.of("target/test-reports");
    generator.generate(result, outputDir);

    // Verify files exist
    assertTrue(Files.exists(outputDir.resolve("report-summary.html")));

    // Print path for easy access
    System.out.println("Report: " + outputDir.toAbsolutePath());
}
```

---

## Differences from Standard Thymeleaf

This section documents key differences when coming from standard Thymeleaf. These are important gotchas to be aware of.

### Array/Collection Methods

Since expressions use JavaScript, collection methods differ from Java:

| Thymeleaf (Java) | Karate (JavaScript) | Notes |
|------------------|---------------------|-------|
| `list.size()` | `list.length` | Arrays use `.length` property |
| `list.isEmpty()` | `list.length == 0` | No `.isEmpty()` method |
| `map.size()` | N/A | Maps need conversion (see below) |

```html
<!-- WRONG: Java-style -->
<span th:text="items.size()">0</span>

<!-- CORRECT: JavaScript-style -->
<span th:text="items.length">0</span>

<!-- Conditional on empty -->
<div th:if="items.length == 0">No items</div>
<div th:if="items.length > 0">Found items</div>
```

### Map Iteration

Maps are automatically converted to lists of entry objects when iterated, providing `entry.key` and `entry.value` access like standard Thymeleaf:

```html
<!-- Iterating over a Map -->
<div th:each="entry : someMap">
  <span th:text="entry.key">key</span>
  <span th:text="entry.value">value</span>
</div>

<!-- With nested values -->
<div th:each="entry : tagMap">
  <h3 th:text="entry.key">@tagName</h3>
  <ul>
    <li th:each="item : entry.value" th:text="item.name">Item</li>
  </ul>
</div>
```

This works because Maps are converted to a list of `{key: ..., value: ...}` objects during iteration.

### `th:attr` with Hyphenated Attributes

Hyphenated attribute names (like `data-bs-target`) must be **quoted** in `th:attr` expressions. Without quotes, the hyphens are interpreted as subtraction operators.

```html
<!-- WRONG: hyphens parsed as subtraction -->
<button th:attr="data-bs-target:'#item-' + id">Click</button>

<!-- CORRECT: quote the attribute name -->
<button th:attr="'data-bs-target':'#item-' + id, 'data-id':id">Click</button>
```

**Result:**
```html
<button data-bs-target="#item-42" data-id="42">Click</button>
```

This is consistent with JavaScript object literal syntax where hyphenated keys must be quoted.

### Iteration Status Variable

The iteration status variable syntax differs slightly:

```html
<!-- Standard Thymeleaf uses iterStat -->
<tr th:each="item, iterStat : items">
  <td th:text="iterStat.index">0</td>
  <td th:if="iterStat.first">First!</td>
</tr>

<!-- Karate uses iter (shorter) -->
<tr th:each="item, iter : items">
  <td th:text="iter.index">0</td>
  <td th:if="iter.first">First!</td>
</tr>
```

Available properties: `iter.index`, `iter.count`, `iter.first`, `iter.last`, `iter.even`, `iter.odd`

### Fragment Expression Auto-Wrapping

Karate auto-wraps simple template paths with `~{}` for convenience:

```html
<!-- Standard Thymeleaf requires explicit ~{} -->
<div th:insert="~{header}"></div>

<!-- Karate allows simplified syntax -->
<div th:insert="header"></div>
```

Both syntaxes work in Karate. If you include `~{}`, it won't be double-wrapped:

| Standard Thymeleaf | Karate Equivalent | Notes |
|--------------------|-------------------|-------|
| `th:insert="~{header}"` | `th:insert="header"` | Simplified |
| `th:replace="~{footer}"` | `th:replace="footer"` | Simplified |
| `th:replace="~{layout :: content}"` | Same | Use full syntax for selectors |
| `th:insert="~{:: localFrag}"` | Same | Use full syntax for same-file fragments |

**When to use full `~{}` syntax:**
- Named fragment selectors: `~{template :: fragmentName}`
- CSS selectors: `~{template :: .css-class}`
- Same-file fragments: `~{:: fragmentName}`

### HTMX Event Handlers — Avoid `::`

HTMX's shorthand event syntax `hx-on::after-request` uses `::` which Thymeleaf parses as a fragment expression — this causes a `TemplateInputException` with no useful line number. Use the long-form `hx-on-htmx-after-request` or plain `onclick` with `fetch()` instead.

```html
<!-- BREAKS — Thymeleaf parses :: as fragment expression -->
<button hx-post="/api/sessions" hx-on::after-request="location.reload()">New</button>

<!-- WORKS — HTMX long-form event syntax -->
<button hx-post="/api/sessions" hx-on-htmx-after-request="location.reload()">New</button>

<!-- WORKS — plain onclick with fetch (simplest, no HTMX event wiring needed) -->
<button onclick="fetch('/api/sessions',{method:'POST'}).then(function(){location.reload()})">New</button>
```

### Colons in String Literals

Colons in `th:text` string concatenation can confuse the parser when they appear near ternary-like patterns. If you get parse errors with `:` in strings, split into `th:if`/`th:unless`:

```html
<!-- MAY BREAK — parser can misinterpret : as ternary separator -->
<td th:text="s.host ? s.host + ':' + s.port : '-'"></td>

<!-- WORKS — split into conditional spans -->
<td>
  <span th:if="s.host" th:text="s.host"></span>
  <span th:unless="s.host">-</span>
</td>
```

### Dynamic HTMX Attributes with `th:attr`

When you need HTMX attributes whose values come from template expressions, use `th:attr` with **quoted hyphenated names** and **colon separator**:

```html
<!-- Set hx-delete dynamically from a session ID -->
<button th:attr="'hx-delete': '/api/sessions/' + session.id"
        hx-on-htmx-after-request="location.href='/'">
  Delete
</button>

<!-- Set hx-get dynamically -->
<div th:attr="'hx-get': '/api/items/' + item.id, 'hx-target': '#detail-' + item.id"
     hx-trigger="click" hx-swap="innerHTML">
  Click for details
</div>
```

**Alternative — use `onclick` with `fetch()` for simple cases.** This avoids `th:attr` entirely and is easier to debug:

```html
<button th:attr="onclick: 'fetch(\'/api/sessions/' + s.id + '\',{method:\'DELETE\'}).then(function(){location.reload()})'"
        title="Destroy">
  Delete
</button>
```

### Serving REST APIs Alongside Templates

A common pattern: the same server serves both HTML templates and JSON REST endpoints. Route by path prefix in your request handler:

```java
httpServer = HttpServer.start(port, request -> {
    String path = request.getPath();
    if (path.startsWith("/api/")) {
        return handleApi(request);        // Return JSON responses
    }
    return templateHandler.apply(request); // Render HTML templates
});
```

REST responses use `HttpResponse.setBody(Map)` or `setBody(List)` which auto-serializes to JSON. Template responses go through `ServerRequestHandler`.

### Template Debugging

**Thymeleaf parse errors are opaque** — the error message says which top-level template failed but not which line, expression, or included sub-template caused it. The only reliable debugging approach:

1. Temporarily replace the template content with a minimal version (`<p>test</p>`)
2. Confirm it loads
3. Add sections back one at a time until the error returns
4. The last section added contains the broken expression

**Common causes of silent parse failures:**
- `::` anywhere in HTMX attributes
- Unquoted hyphenated names in `th:attr`
- `:` in string literals near ternary expressions
- Mismatched quotes in `th:attr` values

**Dev mode helps** — set `KARATE_DEV_MODE=true` so templates reload from filesystem on every request. No server restart needed for template changes.

---

## Using Templates from Karate Features

The `doc` keyword allows rendering HTML templates directly from Karate feature files. This is useful for generating custom reports, documentation, or any HTML output during test execution.

### Basic Usage

```cucumber
Feature: Generate Report

Scenario: Create user report
  * def users = [{name: 'Alice', role: 'Admin'}, {name: 'Bob', role: 'User'}]
  * def reportTitle = 'User Summary'
  * doc 'user-report.html'
```

**user-report.html:**
```html
<!DOCTYPE html>
<html>
<head><title th:text="reportTitle">Report</title></head>
<body>
  <h1 th:text="reportTitle">Title</h1>
  <table>
    <tr th:each="user : users">
      <td th:text="user.name">Name</td>
      <td th:text="user.role">Role</td>
    </tr>
  </table>
</body>
</html>
```

### Syntax Options

```cucumber
# String path (resolves relative to feature file)
* doc 'template.html'

# Map syntax with read key
* doc { read: 'reports/summary.html' }
```

### Path Resolution

Templates are resolved relative to the feature file's parent directory:

| Path | Resolution |
|------|------------|
| `template.html` | Relative to feature file directory |
| `reports/summary.html` | Subdirectory relative to feature |
| `/templates/report.html` | Leading `/` stripped, still relative to feature directory |
| `classpath:templates/report.html` | Classpath lookup (for templates in JARs) |

**Important:** A path starting with `/` does NOT resolve to the filesystem root. It is treated as relative to the feature file's parent directory (the leading `/` is stripped). This matches V1 behavior.

### Variable Access

All scenario variables are automatically available in templates:

```cucumber
* def title = 'My Report'
* def items = [{id: 1, name: 'Item 1'}, {id: 2, name: 'Item 2'}]
* def config = { showDetails: true }
* doc 'report.html'
```

```html
<h1 th:text="title">Title</h1>
<div th:if="config.showDetails">
  <ul>
    <li th:each="item : items" th:text="item.name">Item</li>
  </ul>
</div>
```

### Report Integration

The rendered HTML is automatically embedded in the step result, making it visible in HTML reports. The embed appears with MIME type `text/html`.

### Programmatic Access

For advanced use cases, you can capture the rendered HTML:

```java
// In Java/test setup
karateJs.setOnDoc(html -> {
    // Process the rendered HTML
    saveToFile(html, "output.html");
});
```

---

## Summary

| Feature | Syntax | Description |
|---------|--------|-------------|
| Text output | `th:text="expr"` | Replace element content |
| Conditional | `th:if="expr"` / `th:unless="expr"` | Show/hide elements |
| Loop | `th:each="item: list"` | Iterate arrays |
| Include | `th:replace="file.html"` | Include templates |
| Server JS | `<script ka:scope="global">` | Execute server-side JS |
| Pass data | `_.variable = value` | From script to template |
| Session | `session.key = value` | Persist across requests |
| Request data | `request.param('name')` | Get form/query params |
| Redirect | `context.redirect('/path')` | HTTP redirect |
| HTMX GET | `ka:get="/path"` | AJAX GET request |
| HTMX POST | `ka:post="/path"` | AJAX POST request |
| HTMX data | `ka:vals="key:value"` | Send data with request |
| HTMX target | `ka:target="#id"` | Update target element |
| HTMX swap | `ka:swap="outerHTML"` | How to update content |
| Alpine binding | `ka:data="var:expr"` | Bind server data to AlpineJS form |
| Global vars | `ServerConfig.globalVariables(map)` | Inject Java utils into all templates/APIs |
| Karate doc | `* doc 'template.html'` | Render template from feature file |

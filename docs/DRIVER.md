# Browser Driver Design

> **Internal implementation document** for browser automation in karate.
> WebSocket infrastructure: `io.karatelabs.http` package (WsClient, WsFrame, CdpClient)

## Progress Tracking

| Phase | Description | Status |
|-------|-------------|--------|
| **1-8** | CDP Driver (WebSocket + launch + elements + frames + intercept) | ✅ Complete |
| **9a** | Gherkin/DSL Integration | ✅ Complete |
| **9b** | Gherkin E2E Tests | ✅ Complete |
| **9c** | PooledDriverProvider (browser reuse) | ✅ Complete |
| **10** | Playwright Backend | ⬜ Not started |
| **11** | W3C WebDriver Backend | ✅ Complete (100% pass, separate `w3c` Maven profile + CI job) |
| **12** | WebDriver BiDi (Future) | ⬜ Not started |
| **13** | Cloud Provider Integration | ⬜ Not started |

**Deferred:** Capabilities query API, Video recording (→ commercial app), karate-robot

---

## Package Structure

```
io.karatelabs.driver/
├── Driver, Element, Locators, Finder    # Backend-agnostic API
├── DriverOptions, DriverException       # Configuration and errors
├── Mouse, Keys, Dialog                   # Input interfaces
├── DialogHandler, InterceptHandler       # Functional interfaces
├── InterceptRequest, InterceptResponse   # Data classes
├── DriverProvider, PooledDriverProvider  # Lifecycle management
├── PageLoadStrategy                      # Enum
└── cdp/                                  # CDP implementation
    ├── CdpDriver, CdpMouse, CdpKeys, CdpDialog
    ├── CdpClient, CdpMessage, CdpEvent, CdpResponse
    └── CdpLauncher, CdpDriverOptions
```

---

## V1 Compatibility

| Aspect | V2 Approach |
|--------|-------------|
| Gherkin DSL | **Drop-in compatible** - same keywords (`click()`, `html()`, etc.) |
| Java API | **Clean break** - redesigned, not constrained by v1 quirks |
| `Target` interface | Replaced by `DriverProvider` (simpler, more flexible) |
| `@AutoDef`, Plugin | Removed |
| `getDialogText()` polling | Replaced by `onDialog(handler)` callback |
| `showDriverLog` | No effect (TODO: implement driver log forwarding) |

**Gherkin Syntax (unchanged from v1):**
```gherkin
* configure driver = { type: 'chrome', headless: true }
* driver serverUrl + '/login'
* input('#username', 'admin')
* click('button[type=submit]')
* waitFor('#dashboard')
* match driver.title == 'Welcome'
```

### V1 API Compatibility Table

*Navigation:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `driver 'url'` | ✅ Working |
| `driver.url` | ✅ Working |
| `driver.title` | ✅ Working |
| `refresh()` | ✅ Working |
| `reload()` | ✅ Working |
| `back()` | ✅ Working |
| `forward()` | ✅ Working |

*Element Actions:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `click(locator)` | ✅ Working |
| `input(locator, value)` | ✅ Working |
| `input(locator, ['a', Key.ENTER])` | ✅ Working |
| `focus(locator)` | ✅ Working |
| `clear(locator)` | ✅ Working |
| `value(locator, value)` | ✅ Working |
| `select(locator, text)` | ✅ Working |
| `scroll(locator)` | ✅ Working |
| `highlight(locator)` | ✅ Working |

*Element State:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `html(locator)` | ✅ Working |
| `text(locator)` | ✅ Working |
| `value(locator)` | ✅ Working |
| `attribute(locator, name)` | ✅ Working |
| `enabled(locator)` | ✅ Working |
| `exists(locator)` | ✅ Working |
| `position(locator)` | ✅ Working |

*Element Navigation:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `element.parent` | ✅ Working — returns parent Element, chainable (`e.parent.parent`) |
| `element.closest(selector)` | ✅ Working (new in v2) — nearest ancestor matching CSS selector |

*Wait Methods:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `waitFor(locator)` | ✅ Working |
| `waitForAny(loc1, loc2)` | ✅ Working |
| `waitForUrl('path')` | ✅ Working |
| `waitForText(loc, text)` | ✅ Working |
| `waitForEnabled(loc)` | ✅ Working |
| `waitForResultCount(loc, n)` | ✅ Working |
| `waitUntil('js')` | ✅ Working |
| `waitUntil(loc, 'js')` | ✅ Working |

*Frames/Dialogs/Cookies:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `switchFrame(index)` | ✅ Working |
| `switchFrame(locator)` | ✅ Working |
| `switchFrame(null)` | ✅ Working |
| `dialog(accept)` | ✅ Working |
| `cookie(name)` | ✅ Working |
| `clearCookies()` | ✅ Working |
| `driver.intercept(config)` | ✅ Working (CDP only, supports `mock` and `handler`) |

*Mouse/Keys:*
| V1 Gherkin | V2 Status |
|------------|-----------|
| `mouse()` | ✅ Working |
| `mouse(locator)` | ✅ Working |
| `keys()` | ✅ Working |
| `Key.ENTER`, `Key.TAB` | ✅ Working |

### Driver Lifecycle (V1 Behavior)

V2 preserves V1's driver lifecycle behavior for called features:

**Auto-Start on First Use:**
```gherkin
# In karate-config.js
karate.configure('driver', { type: 'chrome', headless: true })

# In feature file - driver auto-starts when keyword is encountered
* driver serverUrl + '/login'
```

When `driver url` is encountered:
1. If driver is null or terminated, `initDriver()` is called
2. Driver is created using the configured `driverConfig`
3. If `DriverProvider` is set (via `Runner.driverProvider()`), it's used to acquire the driver
4. Otherwise, driver is created directly based on config

**Config Inheritance for Called Features:**

When a feature calls another feature (`call read('sub.feature')`), the called feature inherits:
- **All config** from the caller (via `KarateConfig.copyFrom()`) - including driverConfig, ssl, proxy, headers, cookies, timeouts, etc.
- All variables from the caller
- The driver instance (if already initialized in the caller)

This matches V1 behavior where the entire `Config` object is copied/shared with called features.

```gherkin
# main.feature - Scenario Outline entry point
Scenario Outline: <config>
* call read('orchestration.feature')

# orchestration.feature - receives inherited driverConfig
Background:
* driver serverUrl + '/index.html'  # auto-starts using inherited config

Scenario:
* call read('sub.feature')  # sub inherits the driver instance

# sub.feature - uses inherited driver, no new browser opened
Scenario:
* driver serverUrl + '/page2'  # same browser, navigates to new page
* match driver.title == 'Page 2'
```

**Driver Not Closed Until Top-Level Exit:**

The driver is only closed when the top-level scenario (the entry point) completes:
- Called features mark their driver as "inherited" (`driverInherited = true`)
- Inherited drivers are not closed when the callee scenario exits
- Only the owner (the scenario that created the driver) closes it

**Driver Upward Propagation (Automatic):**

For shared-scope calls (`call read('...')` without a result variable), drivers automatically propagate back to the caller — matching V1 behavior. No special configuration is needed.

```gherkin
# init-driver.feature - called feature creates driver
@ignore
Feature: Initialize driver

Background:
* configure driver = driverConfig

Scenario: Init driver
* driver serverUrl + '/page.html'  # driver auto-propagates to caller
```

```gherkin
# main.feature - caller receives the driver
Scenario:
* call read('init-driver.feature')
* match driver.title == 'Page'  # works - driver propagated from callee
```

| Scenario | Behavior |
|----------|----------|
| Caller has driver, callee inherits | ✅ Driver shared |
| Callee inits driver, shared-scope call | ✅ Propagated to caller |
| Callee inits driver, isolated-scope call (`def result = call ...`) | Released to pool |

**Two Approaches to Driver Management:**

| Approach | Use Case | How It Works |
|----------|----------|--------------|
| **Default (PooledDriverProvider)** | Parallel tests, browser pooling | Auto-created, pool size = thread count |
| **Custom DriverProvider** | Containers, cloud providers | Set via `Runner.driverProvider()` |

How it works:
- `configure driver` sets driver options (timeout, headless, etc.)
- PooledDriverProvider is auto-created by default (pool size = parallel thread count)
- Custom DriverProvider (if set) overrides the default

```java
// Example: DriverProvider with config from karate-config.js
Runner.path("features/")
    .configDir("classpath:karate-config.js")  // sets driverConfig
    .driverProvider(new PooledDriverProvider()) // uses driverConfig
    .parallel(4);
```

---

## Driver Interface

### Navigation
```java
void setUrl(String url)                          // Navigate and wait
String getUrl()                                  // Get current URL
String getTitle()                                // Get page title
void waitForPageLoad(PageLoadStrategy strategy)  // Wait for load
void waitForPageLoad(PageLoadStrategy, Duration) // With timeout
void refresh()                                   // Soft reload
void reload()                                    // Hard reload
void back()                                      // Navigate back
void forward()                                   // Navigate forward
```

### JavaScript Evaluation
```java
Object script(String expression)                 // Execute JS
Object script(String locator, String expression) // JS on element (_ = element)
List<Object> scriptAll(String locator, String expression)
```

### Screenshot
```java
byte[] screenshot()                              // PNG bytes
byte[] screenshot(boolean embed)                 // Optional embed in report
```

### Dialog Handling
```java
void onDialog(DialogHandler handler)             // Register callback
String getDialogText()                           // Get message
void dialog(boolean accept)                      // Accept/dismiss
void dialog(boolean accept, String input)        // With prompt input
```

### Frame Switching
```java
void switchFrame(int index)                      // By index
void switchFrame(String locator)                 // By locator (null = main)
Map<String, Object> getCurrentFrame()            // Get frame info
```

### Element Operations
```java
Element click(String locator)
Element focus(String locator)
Element clear(String locator)
Element input(String locator, String value)
Element value(String locator, String value)      // Set value
Element select(String locator, String text)
Element select(String locator, int index)
Element scroll(String locator)
Element highlight(String locator)
```

**Select Matching Behavior:**

| Syntax | Behavior |
|--------|----------|
| `select(loc, 'us')` | Match by value first, then fall back to text |
| `select(loc, 'United States')` | Falls back to text match if value not found |
| `select(loc, '{}United States')` | Match by exact text only |
| `select(loc, '{^}Unit')` | Match by text contains |
| `select(loc, 1)` | Match by index (0-based) |

Events dispatched: `input` then `change`, both with `{bubbles: true}` for React/Vue compatibility.

### Element State
```java
String text(String locator)
String html(String locator)
String value(String locator)                     // Get value
String attribute(String locator, String name)
Object property(String locator, String name)
boolean enabled(String locator)
boolean exists(String locator)
Map<String, Object> position(String locator)
Map<String, Object> position(String locator, boolean relative)
```

### Locators
```java
Element locate(String locator)
List<Element> locateAll(String locator)
Element optional(String locator)                 // No throw if missing
```

### Element Navigation
```java
// On Element — walk the DOM without leaving the driver API
Element parent()                                  // Parent Element, or non-existent if root
Element closest(String selector)                  // Nearest ancestor (or self) matching CSS
```

Both return an `Element`. The returned element carries a pure-JS locator, so it composes with every other element op — `e.parent.parent.script(...)`, `e.closest('form').attribute('id')`, `waitFor(e.parent.getLocator())`.

Prefer `closest(selector)` over chained `parent` calls when the structural contract is "some ancestor matches X" — it's robust to markup shuffling and reads like the native `Element.closest()` DOM API.

```gherkin
# Walk up from a labelled input to its form
* def form = locate('#username').closest('form')
* match form.attribute('id') == 'test-form'

# Single-hop parent
* def group = locate('#username').parent
* match group.attribute('class') == 'form-group'
```

### Wait Methods
```java
Element waitFor(String locator)
Element waitFor(String locator, Duration timeout)
Element waitForAny(String locator1, String locator2)
Element waitForAny(String[] locators)
Element waitForAny(String[] locators, Duration timeout)
Element waitForText(String locator, String expected)
Element waitForText(String locator, String expected, Duration timeout)
Element waitForEnabled(String locator)
Element waitForEnabled(String locator, Duration timeout)
String waitForUrl(String expected)
String waitForUrl(String expected, Duration timeout)
Element waitUntil(String locator, String expression)
Element waitUntil(String locator, String expression, Duration timeout)
boolean waitUntil(String expression)
boolean waitUntil(String expression, Duration timeout)
Object waitUntil(Supplier<Object> condition)
Object waitUntil(Supplier<Object> condition, Duration timeout)
List<Element> waitForResultCount(String locator, int count)
List<Element> waitForResultCount(String locator, int count, Duration timeout)
```

### Cookies
```java
Map<String, Object> cookie(String name)          // Get cookie
void cookie(Map<String, Object> cookie)          // Set cookie
void deleteCookie(String name)
void clearCookies()
List<Map<String, Object>> getCookies()
```

### Window Management
```java
void maximize()
void minimize()
void fullscreen()
Map<String, Object> getDimensions()
void setDimensions(Map<String, Object> dimensions)
void activate()                                  // Bring to front
```

### PDF Generation
```java
byte[] pdf(Map<String, Object> options)
byte[] pdf()
```

### Mouse and Keyboard
```java
Mouse mouse()                                    // At (0, 0)
Mouse mouse(String locator)                      // At element center
Mouse mouse(Number x, Number y)                  // At coordinates
Keys keys()
```

**Keyboard Implementation Notes (CdpKeys):**
- Uses v1-compatible 3-event sequence: `rawKeyDown` → `char` → `keyUp`
- Enter key sends `text: "\r"` (required for form submission)
- Punctuation uses proper `windowsVirtualKeyCode` (e.g., `.` = 190, `,` = 188)
- Special keys (Tab, Enter, Backspace) handled separately from printable chars

### Pages/Tabs
```java
List<String> getPages()
void switchPage(String titleOrUrl)
void switchPage(int index)
```

### Positional Locators
```java
Finder rightOf(String locator)
Finder leftOf(String locator)
Finder above(String locator)
Finder below(String locator)
Finder near(String locator)
```

### Request Interception
```java
void intercept(List<String> patterns, InterceptHandler handler)
void intercept(InterceptHandler handler)         // All requests
void stopIntercept()
```

### Lifecycle
```java
void quit()
void close()                                     // Alias for quit()
boolean isTerminated()
DriverOptions getOptions()
```

---

## JS API

### Binding: `karate.driver(config)`

The driver is exposed to JavaScript through `karate.driver(config)`:

```javascript
// Create driver instance
var driver = karate.driver({ type: 'chrome', headless: true })

// Navigate
driver.setUrl('http://localhost:8080/login')

// Interact
driver.input('#username', 'admin')
driver.click('button[type=submit]')
driver.waitFor('#dashboard')

// Read state
var title = driver.title      // Property access via ObjectLike
var url = driver.url
var cookies = driver.cookies

// Cleanup
driver.quit()
```

### ObjectLike Property Access

Driver implements `ObjectLike` for JS property access:

```java
default Object get(String name) {
    return switch (name) {
        case "url" -> getUrl();
        case "title" -> getTitle();
        case "cookies" -> getCookies();
        default -> null;
    };
}
```

**Accessible properties:**
- `driver.url` → `getUrl()`
- `driver.title` → `getTitle()`
- `driver.cookies` → `getCookies()`

### Gherkin Keyword Mapping

| Gherkin | JS Equivalent |
|---------|---------------|
| `* driver 'url'` | `driver.setUrl('url')` |
| `* click('#id')` | `driver.click('#id')` |
| `* input('#id', 'val')` | `driver.input('#id', 'val')` |
| `* waitFor('#id')` | `driver.waitFor('#id')` |
| `* match driver.title == 'x'` | `driver.title` |

### Root Bindings

Driver methods are bound as globals for Gherkin compatibility:

```java
engine.putRootBinding("click", (ctx, args) -> driver.click(args[0].toString()));
engine.putRootBinding("input", (ctx, args) -> driver.input(args[0].toString(), args[1].toString()));
engine.putRootBinding("Key", new JavaType(Keys.class));
// ... all driver methods
```

**Hidden from `getAllVariables()`** - keeps reports clean.

---

## Wait System

### Philosophy: Auto-Wait by Default

V2 automatically waits for elements to exist before performing operations. This reduces flaky tests caused by timing issues where elements haven't yet appeared in the DOM.

**Auto-wait is built into all element operations:**
- `click()`, `input()`, `value()`, `select()`, `focus()`, `clear()`
- `scroll()`, `highlight()`
- `text()`, `html()`, `attribute()`, `property()`, `enabled()`
- `position()`, `script(locator, expr)`, `scriptAll(locator, expr)`

**How it works:**
1. Before each operation, checks if element exists
2. If not, polls with `retryInterval` (default 500ms) up to `retryCount` times (default 3)
3. Throws `DriverException` if element still not found after retries

**Configuration:**
```gherkin
* configure driver = { retryCount: 5, retryInterval: 200 }
```

**Note:** `exists()` does NOT auto-wait - it immediately returns true/false. Use `waitFor()` for explicit waiting with longer timeouts.

### Override with Explicit Waits

```gherkin
# Default: auto-wait before click
* click('#button')

# Explicit: custom wait before action
* waitFor('#button').click()
* waitForEnabled('#button').click()

# Extended timeout
* retry(5, 10000).click('#button')
```

### Retry Configuration

Fixed interval polling (v1-style):

```java
driver.timeout(Duration.ofSeconds(30))     // Default timeout
driver.retryInterval(Duration.ofMillis(500)) // Poll interval
```

### Wait Methods

| Method | Behavior |
|--------|----------|
| `waitFor(locator)` | Wait until element exists |
| `waitForAny(locators)` | Wait for any match |
| `waitForText(loc, text)` | Wait for text content |
| `waitForEnabled(loc)` | Wait until not disabled |
| `waitForUrl(substring)` | Wait for URL to contain |
| `waitUntil(expression)` | Wait for JS truthy |
| `waitUntil(loc, expr)` | Wait for element + JS |
| `waitForResultCount(loc, n)` | Wait for element count |

### JS Syntax for waitUntil

Arrow function syntax in JS API (not underscore shorthand):

```javascript
// JS API - arrow function
driver.waitUntil('#btn', el => !el.disabled)
driver.waitUntil('#btn', el => el.textContent.includes('Ready'))

// Gherkin - underscore shorthand (v1 compat)
* waitUntil('#btn', '!_.disabled')
```

---

## DriverProvider (Browser Reuse)

### Interface

```java
public interface DriverProvider {
    Driver acquire(ScenarioRuntime runtime, Map<String, Object> config);
    void release(ScenarioRuntime runtime, Driver driver);
    void shutdown();
}
```

### PooledDriverProvider

Built-in implementation for parallel execution:

```java
Runner.path("features/")
    .driverProvider(new PooledDriverProvider())
    .parallel(4);  // Creates pool of 4 drivers
```

**Features:**
- Pool size auto-detected from `Runner.parallel(N)`
- Works correctly with virtual threads
- Resets state between scenarios (`about:blank`, clear cookies)

### Custom Provider (Testcontainers)

```java
public class ContainerDriverProvider extends PooledDriverProvider {
    private final ChromeContainer container;

    public ContainerDriverProvider(ChromeContainer container) {
        super();
        this.container = container;
    }

    @Override
    protected Driver createDriver(Map<String, Object> config) {
        return CdpDriver.connect(container.getCdpUrl(), CdpDriverOptions.fromMap(config));
    }
}
```

### Cloud Provider Pattern

See [Cloud Provider Integration](#cloud-provider-integration) for SauceLabs, BrowserStack examples.

### Interaction with `configure driver`

`DriverProvider` works alongside `configure driver`:

1. `configure driver` in karate-config.js sets driver options (timeout, headless, etc.)
2. When `driver url` is encountered, `ScenarioRuntime.initDriver()` checks for a provider
3. If provider exists, it calls `provider.acquire(runtime, configMap)` with the config
4. The provider can use or override the config when creating the driver

```gherkin
# karate-config.js
karate.configure('driver', { timeout: 30000, headless: true })

# feature file
* driver serverUrl + '/login'  # uses provider if set, passes config
```

This allows:
- Config (timeout, options) to be centralized in karate-config.js
- Browser lifecycle to be managed by provider (pooling, containers, cloud)
- Both approaches to coexist without conflict

---

## Multi-Backend Architecture

### Backend Selection

`type` implies backend (v1 style):

| Config | Backend |
|--------|---------|
| `type: 'chrome'` | CDP |
| `type: 'playwright'` | Playwright |
| `type: 'chromedriver'` | WebDriver |

### Feature Matrix

| Feature | CDP | Playwright | WebDriver |
|---------|-----|------------|-----------|
| Navigation | ✅ | ✅ | ✅ |
| Element actions | ✅ | ✅ | ✅ |
| Wait methods | ✅ | ✅ | ✅ |
| Screenshots | ✅ | ✅ | ✅ |
| Frames | ✅ Explicit | ✅ Auto | ✅ Explicit |
| Dialogs | ✅ Callback | ✅ Callback | ⚠️ Limited |
| Request interception | ✅ | ✅ | ❌ |
| PDF generation | ✅ | ✅ | ❌ |
| `ariaTree()` | ✅ | ❌ (future) | ❌ |
| Raw protocol access | ✅ `cdp()` | ❌ | ❌ |

### Unsupported Operations

Throw `UnsupportedOperationException` at runtime:

```java
public byte[] pdf() {
    throw new UnsupportedOperationException(
        "PDF generation not supported on WebDriver backend");
}
```

### Chrome Executable Resolution

`CdpLauncher` resolves the browser executable in this order:

1. **`executable` option** — `configure driver = { executable: '/path/to/chrome' }`
2. **`KARATE_CHROME_EXECUTABLE` env var** — useful for Docker/CI where Chromium is at a non-standard path
3. **Platform default** — `/Applications/Google Chrome.app/...` (macOS), `/usr/bin/google-chrome` (Linux), `C:\Program Files\Google\Chrome\...` (Windows)

| Env Var | Purpose |
|---------|---------|
| `KARATE_CHROME_EXECUTABLE` | Override Chrome/Chromium executable path |
| `KARATE_CHROME_ARGS` | Extra args (space-separated), appended to every launch |
| `KARATE_DRIVER_HEADLESS` | Run browser headless when set to `true` |

```bash
# Docker: Debian Chromium at /usr/bin/chromium
export KARATE_CHROME_EXECUTABLE=/usr/bin/chromium
export KARATE_CHROME_ARGS="--no-sandbox --disable-gpu --disable-dev-shm-usage"

# CI: custom Chrome install
export KARATE_CHROME_EXECUTABLE=/opt/chrome/chrome
```

### CDP Backend (Current)

- Custom `CdpClient` WebSocket implementation
- Chrome DevTools Protocol
- Full feature set

### Playwright Backend (Phase 10)

**Architecture:**
- Node subprocess (not Java SDK)
- Custom `PlaywrightClient` for wire protocol
- Goal: better than Playwright Java library

**Frame handling:**
- Auto-frame detection by default
- Explicit `switchFrame()` as fallback for cross-backend tests

**MVP Definition:**
- All Gherkin E2E tests must pass
- All JS API E2E tests must pass

### W3C WebDriver Backend (Phase 11) - Experimental

**Status:** Core implementation complete. Infrastructure works (session creation, navigation, element operations). Many edge cases still failing in E2E tests. Marked as experimental until stabilized.

**Architecture:**
- Single `W3cDriver` class in `io.karatelabs.driver.w3c` package
- `W3cBrowserType` enum drives all browser-specific differences (port, executable, CLI args, capabilities)
- `W3cSession` uses `java.net.http.HttpClient` for W3C protocol (clean separation from scenario HTTP)
- `W3cElement` extends `BaseElement` with native W3C element ID operations
- `W3cDriverOptions` supports `webDriverUrl`, `webDriverSession`, `capabilities`, `start` (v1-compatible)
- `W3cKeys` for keyboard input via sendKeys on active element

**Type mapping (v1-compatible):**

| `configure driver` type | Backend | Executable | Default Port |
|---|---|---|---|
| `chrome` (default) | CDP | (Chrome) | 9222 |
| `chromedriver` | W3C | chromedriver | 9515 |
| `geckodriver` | W3C | geckodriver | 4444 |
| `safaridriver` | W3C | safaridriver | 5555 |
| `msedgedriver` | W3C | msedgedriver | 9515 |

**Cloud/Remote configuration:**
```javascript
// SauceLabs / BrowserStack - set webDriverUrl to remote hub
configure driver = {
  type: 'chromedriver',
  webDriverUrl: 'https://ondemand.saucelabs.com:443/wd/hub',
  capabilities: { platformName: 'Windows 10', 'sauce:options': { tunnelId: '...' } }
}

// Local chromedriver already running
configure driver = { type: 'chromedriver', webDriverUrl: 'http://localhost:9515' }
```

**Capabilities configuration (v1-compatible):**
- Auto-generates minimal `{ capabilities: { alwaysMatch: { browserName: '...' } } }` from type
- `capabilities` key merges into `alwaysMatch` for convenience
- `webDriverSession` provides full payload override (v1 backward compat)

**Process management:**
- By default, launches the driver executable (chromedriver, geckodriver, etc.) as child process
- If `webDriverUrl` is set, connects to existing server (no process launch)
- Port auto-allocation from `W3cBrowserType.defaultPort`
- On quit: DELETE /session, then kill process

**CDP-only operations (throw `UnsupportedOperationException`):**
- `mouse()` — coordinate-based mouse input
- `pdf()` — PDF generation
- `intercept()` — request interception
- `onDialog()` — dialog callback handler (use `dialog(true/false)` after dialog appears)
- `stopIntercept()` — request interception

**Refactoring done for multi-backend support:**
- `Element` extracted to interface (was concrete class)
- `BaseElement` — locator-based impl that delegates to Driver (works with any backend)
- `PooledDriverProvider.createDriver()` now dispatches on config `type` (CDP or W3C)
- `ScenarioRuntime.initDriver()` detects W3C types and creates W3cDriver accordingly

**Current W3C test results: 110/110 pass (100%)**

Main suite: 92/92 pass (CDP-only scenarios tagged `@cdp` and excluded).
Frame suite: 16/16 pass (dedicated W3cFrameFeatureTest, single-threaded).
Separate Maven profile: `mvn verify -Pw3c -pl karate-core`. Runs in parallel CI job.

**What works:**
- [x] Session creation and lifecycle (POST /session, DELETE /session)
- [x] Navigation (url, back, forward, refresh) — 3/3 pass
- [x] Element operations (click, input, clear, focus, text, html, value, attribute, enabled, exists, scroll, highlight, select) — 43/44 pass
- [x] Element finding via JS eval (supports all Karate locator types: CSS, XPath, wildcard, shadow DOM)
- [x] `__kjs` (driver.js) runtime injection (wildcard locators, shadow DOM traversal) — same as CDP
- [x] Frame switching — 16/16 pass (uses W3C element reference directly, improvement over v1 index-finding)
- [x] Screenshots
- [x] Cookies — 6/6 pass (W3C 404 "no such cookie" handled as null)
- [x] Window management (maximize, minimize, fullscreen, setDimensions/getWindowRect)
- [x] Tab/window switching — 1/1 pass (close() uses W3C DELETE /window, auto-switches to remaining handle)
- [x] Shadow DOM — 16/16 pass (deep traversal, wildcard resolution, input)
- [x] Keyboard input via W3C Actions API — 20/21 pass (type, special keys, Ctrl/Shift/Alt combos, plus-notation)
- [x] Feature file calling (call, callonce) — 2/2 pass
- [x] Scenario Outline — 1/1 pass
- [x] Process launch for local driver executables (chromedriver, geckodriver, safaridriver, msedgedriver)
- [x] Remote connection via webDriverUrl (SauceLabs, BrowserStack, Selenium Grid)
- [x] PooledDriverProvider backend-generic dispatch (auto-detects CDP vs W3C from config type)
- [x] Smart `return` prefix for W3C executeScript (detects statement blocks vs value expressions)
- [x] Built-in single-retry on JS errors and locator errors (v1 battle-tested pattern)
- [x] V1-compatible click via JS `.click()` (not W3C element endpoint — more reliable across browsers)
- [x] V1-compatible element state via JS eval (avoids stale element reference errors)
- [x] Input (sendKeys) is the sole native W3C operation (for React/Vue/Angular event handlers)
- [x] Date/time input detection — auto-uses JS value assignment for date fields (sendKeys doesn't work)
- [x] Plus-notation for key combos — `keys().press('Control+a')`, `keys().press('Shift+ArrowLeft')` etc.

**Design decisions (v1 patterns retained):**

| Operation | V1 WebDriver | V2 W3cDriver | Rationale |
|-----------|-------------|-------------|-----------|
| click | JS `.click()` | JS `.click()` | More reliable across browsers, handles shadow DOM |
| text/html/value/attribute | JS eval | JS eval | Avoids stale element references |
| input (sendKeys) | W3C endpoint | W3C endpoint | Only way to trigger framework event handlers |
| clear | JS `value = ''` | JS `value = ''` | More consistent than W3C clear endpoint |
| eval() retry | Single retry on JS error | Single retry on JS error | Handles transient page-load timing |
| elementId retry | Single retry on locator error | Single retry on locator error | Handles transient DOM changes |
| frame switch | Find index by iterating all iframes | W3C element reference directly | Improvement: simpler, no race conditions |
| key combos | W3C Actions API | W3C Actions API | Proper modifier key support |

**CDP-only edge cases (tagged `@cdp`, excluded from W3C suite):**
- keys "Alt+key does not type character" — Alt key behavior is inherently CDP-specific (CDP intercepts at protocol level, W3C delegates to browser)
- element "Select triggers change event with bubbles" — event dispatch sequence differs between CDP and W3C executeScript contexts

**Completed:**
- [x] W3C test suite: 110/110 pass (100%)
- [x] Separate Maven profile (`-Pw3c`) for W3C tests — keeps `cicd` fast (~1:30)
- [x] Parallel CI job in `cicd.yml` — `build` (CDP) and `w3c` run concurrently
- [x] Fix `Runner.Builder.tags()` bug — multiple varargs now stored as List (was v2 regression from v1)

**Remaining TODOs:**
- [ ] Add LocalParallelRunner for cross-browser demo (v1 outline pattern with side-by-side windows). **Approach:** create `outline-xbrowser.feature` with examples table for chrome/chromedriver/geckodriver/safaridriver, `karate-config-xbrowser.js`, and `LocalParallelRunner.java` in e2e package. Not part of cicd (requires local browsers).
- [ ] Test with real SauceLabs/BrowserStack endpoint. **Approach:** manual test with `configure driver = { type: 'chromedriver', webDriverUrl: '...', capabilities: { ... } }`. Document working config in MIGRATION_GUIDE.md.
- [ ] Update MIGRATION_GUIDE.md with WebDriver migration section. **Approach:** add section covering type mapping, capabilities config, webDriverUrl, webDriverSession, CDP-only operations, and driver process management.
- [ ] Review and update [karate-docs](https://github.com/karatelabs/karate-docs) Docusaurus site with full UI automation testing documentation for both CDP and W3C backends. **Approach:** add a "Browser Automation" section covering driver types, configuration, CDP vs W3C feature matrix, cloud provider setup, pooling, and the LocalParallelRunner demo.
- [ ] Create release notes and blog post for karate 2.0.0 final release. **Approach:** crisp summary of what's new (see MIGRATION_GUIDE.md "What's New" section for source material), highlight W3C WebDriver support, performance improvements (embedded JS engine, virtual threads), new features (declarative auth, karate.expect, match within, faker), and the ground-up rewrite story.
- [ ] Sync [karate-cli](https://github.com/karatelabs/karate-cli) release with karate 2.0.0 final. **Approach:** update karate-cli to use karate 2.0.0 as backend, verify all CLI options work, cut a matching karate-cli release.

### WebDriver BiDi (Phase 12)

- Add when spec matures (2025+)
- Combines WebDriver compatibility with CDP-like streaming
- May be obtained "for free" via Playwright if they adopt BiDi

---

## Cloud Provider Integration

### DriverProvider Pattern

Cloud providers use WebDriver `se:cdp` capability:

```java
public class SauceLabsDriverProvider implements DriverProvider {
    @Override
    public Driver acquire(ScenarioRuntime runtime, Map<String, Object> config) {
        // 1. POST to SauceLabs /session with capabilities
        String sessionUrl = "https://ondemand.saucelabs.com/wd/hub/session";
        Map<String, Object> caps = buildCapabilities(config);
        JsonNode response = httpClient.post(sessionUrl, caps);

        // 2. Extract se:cdp WebSocket URL from response
        String cdpUrl = response.at("/value/capabilities/se:cdp").asText();

        // 3. Return CdpDriver.connect()
        return CdpDriver.connect(cdpUrl, CdpDriverOptions.fromMap(config));
    }

    @Override
    public void release(ScenarioRuntime runtime, Driver driver) {
        reportTestStatus(runtime);  // PUT pass/fail to cloud API
        driver.quit();
    }
}
```

### Provider CDP Support

| Provider | CDP Support | Method |
|----------|-------------|--------|
| SauceLabs | ✅ | `se:cdp` via WebDriver |
| BrowserStack | ✅ | `se:cdp` via WebDriver |
| LambdaTest | ✅ | `se:cdp` via WebDriver |

---

## Phase Implementation Notes

### Phases 1-8 Summary

| Phase | Summary |
|-------|---------|
| **1** | WebSocket client, CDP message protocol |
| **2** | Browser launch, minimal driver |
| **3** | Testcontainers + ChromeContainer + TestPageServer |
| **4** | Screenshots, DOM, console, network utilities |
| **5** | Locators, Element class, wait methods, v1 bug fixes |
| **6** | Dialog handling (callback), frame switching |
| **7** | Intercept, cookies, window, PDF, Mouse, Keys, Finder |
| **8** | Package restructuring: `Driver` interface + `cdp/` subpackage |

### Phase 9 Notes

**9a: Gherkin/DSL Integration**
- Added `configure driver` support to KarateConfig
- Added `driver` keyword to StepExecutor
- Driver interface extends `ObjectLike` for JS property access
- Bound driver methods as root bindings

**9b: Gherkin E2E Tests**
- `DriverFeatureTest.java` - JUnit runner with Testcontainers
- All v1 features validated in v2

**9c: PooledDriverProvider**
- Replaced ThreadLocalDriverProvider
- Auto-detects pool size from `Runner.parallel(N)`
- Works with virtual threads

### Phase 10 Notes (Playwright - Planned)

**Architecture:**
- Spawn Playwright Node server
- Custom `PlaywrightClient` for WebSocket protocol
- Same `Driver` interface, different implementation

**Goals:**
- Validate multi-backend abstraction works
- All E2E tests pass (Gherkin and JS)
- Same test syntax works on both backends

**Frame handling:**
- Playwright auto-detects frames
- CDP requires explicit `switchFrame()`
- Tests using explicit `switchFrame()` work on both

---

## Test Strategy

### Directory Structure

```
e2e/
├── feature/           # Gherkin E2E tests
│   ├── karate-config.js
│   ├── navigation.feature
│   ├── element.feature
│   ├── cookie.feature
│   ├── mouse.feature
│   ├── keys.feature
│   ├── frame.feature
│   └── dialog.feature
│
└── js/                # JS API E2E tests
    ├── navigation.js
    ├── element.js
    └── ...
```

### Test Suites

| Suite | Purpose |
|-------|---------|
| Gherkin E2E | V1 syntax compatibility, DSL coverage |
| JS API E2E | Pure JS API coverage, LLM workflow validation |

### Backend Compatibility

Both test suites must pass when switching backends:
- Run with `type: 'chrome'` (CDP)
- Run with `type: 'playwright'` (when implemented)

### LLM/ariaTree Tests

- ARIA tree generation
- Ref-based locators
- Stale ref handling
- One-shot workflow pattern

---

## Architecture Decisions

### Decision Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Interface name | `Driver` | V1 familiarity |
| CDP-only APIs | Graceful degradation | Returns null/no-op on WebDriver |
| Async events | Callback handlers | Driver stays pure sync |
| Error handling | Always verbose | AI-agent friendly |
| Docker | Testcontainers + `chromedp/headless-shell` | ~200MB, fast |
| Wait model | Auto-wait + override | Playwright-style, v1 compat |
| Retry | Fixed interval | Simple, predictable |
| JS API | Unified flat | All methods on driver object |
| Backend selection | `type` implies backend | v1 style, familiar |
| Cloud providers | Extension points only | DriverProvider pattern |
| ariaTree impl | CDP-only initially | Simpler scope |

### Timeout Taxonomy

```java
driver.sessionTimeout(Duration)   // Overall session
driver.pageLoadTimeout(Duration)  // Navigation
driver.elementTimeout(Duration)   // Element waits
driver.scriptTimeout(Duration)    // JS execution
```

### Error Philosophy

All errors include:
- Selector used
- Page URL
- Timing information
- Suggested fixes

AI-agent friendly: detailed context aids debugging.

---

## Wildcard Locator Implementation

### Problem

Wildcard locators like `{div}Account` need to:
1. Match visible elements only
2. Match text in child elements (not just direct text nodes)
3. Prefer leaf elements over parents that contain matching descendants
4. Expand roles (e.g., `{button}` matches `<div role="button">`)
5. Count indices consistently with how they're generated

XPath-based solutions have semantic mismatches with JavaScript's DOM APIs (visibility, textContent, etc.), leading to edge cases.

### Solution: Client-Side JavaScript Resolver

Instead of expanding wildcards to XPath, we inject a JavaScript resolver into the browser:

```
{div}text     → window.__kjs.resolve("div", "text", 1, false)
{^div}text    → window.__kjs.resolve("div", "text", 1, true)
{div:2}text   → window.__kjs.resolve("div", "text", 2, false)
```

**Resource:** `karate-core/src/main/resources/io/karatelabs/driver/driver.js`

**Namespace:** `window.__kjs` (Karate JS Runtime):
- `__kjs.resolve(tag, text, index, contains)` - Wildcard resolver
- `__kjs.log(msg, data)` - Log for debugging
- `__kjs.getLogs()` - Get log entries (for LLM debugging)
- `__kjs.clearLogs()` - Clear log entries
- `__kjs.isVisible()`, `__kjs.getVisibleText()` - Shared utilities

### Features

| Feature | Implementation |
|---------|----------------|
| Visibility | Checks `display`, `visibility`, `aria-hidden`, bounding rect |
| Text extraction | TreeWalker over text nodes, excludes hidden ancestors |
| Leaf preference | Skips elements if a matching descendant exists |
| Role expansion | `{button}` → `button, [role="button"], input[type="submit"]` |
| Index counting | Counts only visible, leaf-matched elements |

### Injection Strategy (CdpDriver)

```java
// Load once at class load
private static final String DRIVER_JS = loadResource("driver.js");

// Inject on-demand before wildcard evaluation
public Object script(String expression) {
    if (expression.contains("__kjs")) {
        ensureKjsRuntime();
    }
    return eval(expression);
}

private void ensureKjsRuntime() {
    Boolean exists = (Boolean) evalDirect("typeof window.__kjs !== 'undefined'");
    if (!Boolean.TRUE.equals(exists)) {
        evalDirect(DRIVER_JS);
    }
}
```

### Multi-Backend Considerations

The `driver.js` is pure browser JavaScript - any driver backend can use it:

| Backend | Implementation |
|---------|----------------|
| CDP | Inject via `Runtime.evaluate` |
| Playwright | Inject via `page.evaluate` or `addInitScript` |
| WebDriver | Inject via `executeScript` |

Future backends should implement the same injection pattern:
1. Load `driver.js` from resources
2. Check if `window.__kjs` exists
3. Inject if missing
4. Execute the resolver call

### Role Mappings

The resolver expands certain tags to include ARIA roles:

```javascript
{
    'button': 'button, [role="button"], input[type="submit"], input[type="button"]',
    'a': 'a[href], [role="link"]',
    'select': 'select, [role="combobox"], [role="listbox"]',
    'input': 'input:not([type="hidden"]), textarea, [role="textbox"]'
}
```

---

## Shadow DOM Support

Modern web components (Lit, Shoelace, Material Web, Salesforce Lightning) use Shadow DOM to encapsulate their internals. Standard `document.querySelector()` cannot reach inside shadow roots.

### Strategy: Light DOM First, Shadow Fallback

- **`hasShadowDOM()`** — cached check for any shadow roots on the page. Zero overhead on non-shadow pages.
- **Light DOM first** — all operations try `document.querySelector()` first
- **Shadow fallback** — if not found and shadow DOM exists, recursively search shadow roots
- **Returns actual elements** — shadow elements themselves are returned (not host elements), enabling proper text extraction and interaction

### Shadow DOM Utilities (`window.__kjs`)

| Function | Description |
|----------|-------------|
| `__kjs.hasShadowDOM()` | Cached check: does the page have any shadow roots? |
| `__kjs.querySelectorDeep(sel, root)` | Recursive single-element finder across shadow boundaries |
| `__kjs.querySelectorAllDeep(sel, root)` | Recursive all-elements finder across shadow boundaries |
| `__kjs.qsDeep(sel)` | Convenience: `querySelector` with shadow fallback (used by `Locators.java`) |
| `__kjs.qsaDeep(sel)` | Convenience: `querySelectorAll` with shadow fallback (used by `Locators.java`) |
| `__kjs._getShadowText(shadowRoot)` | Extract visible text from a shadow root |

### What Pierces Shadow DOM

| Operation | Shadow Support | Notes |
|-----------|---------------|-------|
| CSS selectors (`#id`, `[attr]`) | Yes | Via `qsDeep()` fallback in `Locators.java` |
| Wildcard locators (`{button}Text`) | Yes | `resolve()` searches shadow roots after light DOM |
| `getVisibleText()` | Yes | Falls back to shadow root text if no light DOM text |
| `exists()`, `click()`, `input()`, `text()` | Yes | All use `Locators.selector()` which has shadow fallback |
| XPath locators | No | XPath is DOM-level-3, does not support shadow DOM |

### Locators.java Integration

CSS selectors at document scope use conditional shadow fallback:

```java
// Generated JS for selector("#myBtn")
(window.__kjs && window.__kjs.qsDeep
    ? window.__kjs.qsDeep("#myBtn")
    : document.querySelector("#myBtn"))
```

- Checks `window.__kjs.qsDeep` exists before calling (backward compatible)
- Non-document context (e.g., within an element) uses plain `querySelector` (shadow elements already resolved)

---

## Deferred Features

### Commercial JavaFX Application
- Real-time browser visualization (CDP Screencast)
- LLM agent debugging integration
- Record & replay, API test derivation
- Network/console viewers

### karate-robot
- Cross-platform desktop automation
- Coordinate transform from viewport to screen-absolute
- Computer-use agent scenarios

### Video Recording
- CDP `Page.startScreencast` streams frames
- Stitch with ffmpeg for mp4 output
- Deferred to commercial app

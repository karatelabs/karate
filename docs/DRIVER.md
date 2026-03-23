# Browser Driver Design

> **Internal implementation document** for browser automation in karate.
> Prerequisite: [WEBSOCKET.md](./WEBSOCKET.md) (WebSocket infrastructure)

## Progress Tracking

| Phase | Description | Status |
|-------|-------------|--------|
| **1-8** | CDP Driver (WebSocket + launch + elements + frames + intercept) | ✅ Complete |
| **9a** | Gherkin/DSL Integration | ✅ Complete |
| **9b** | Gherkin E2E Tests | ✅ Complete |
| **9c** | PooledDriverProvider (browser reuse) | ✅ Complete |
| **10** | Playwright Backend | ⬜ Not started |
| **11** | WebDriver Backend (Legacy) | ⬜ Not started |
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

**Driver Upward Propagation with `scope: 'caller'`:**

By default, drivers are pooled (PooledDriverProvider is the default). Each scenario acquires a driver from the pool and releases it when done. To get V1-style behavior where a called feature's driver propagates back to the caller, use `scope: 'caller'`.

**Note:** This only applies to shared scope calls (`call read('...')` without a result variable). The `scope` option only affects driver propagation - variables, config, and cookies still follow standard shared/isolated scope rules.

```gherkin
# init-driver.feature - called feature with scope: 'caller'
@ignore
Feature: Initialize driver with caller scope

Background:
# Merge scope into existing driver config
* def driverWithScope = karate.merge(driverConfig, { scope: 'caller' })
* configure driver = driverWithScope

Scenario: Init driver
* driver serverUrl + '/page.html'  # driver will propagate to caller
```

```gherkin
# main.feature - caller receives the driver
Scenario:
* call read('init-driver.feature')
* match driver.title == 'Page'  # works - driver propagated from callee
```

| Scenario | Default (scope: 'scenario') | scope: 'caller' |
|----------|----------------------------|-----------------|
| Caller has driver, callee inherits | ✅ Driver shared | ✅ Driver shared |
| Callee inits driver, propagates to caller | ❌ Released to pool | ✅ Propagated to caller |

**When to use `scope: 'caller'`:**
- V1 migration where called features initialize the driver
- Reusable "driver setup" features that callers depend on
- Scenario Outlines that call orchestration features

**Default `scope: 'scenario'` is recommended for:**
- New tests using parallel execution
- Tests using containerized browsers
- Any test that doesn't need V1-style propagation

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

### WebDriver Backend (Phase 11)

- Legacy support, lower priority
- Sync REST/HTTP to chromedriver, geckodriver
- W3C WebDriver spec compliance

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

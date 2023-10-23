/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.playwright.driver;

import com.intuit.karate.core.*;
import com.intuit.karate.driver.Mouse;
import com.intuit.karate.driver.*;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.http.Response;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.graalvm.polyglot.Value;

import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Implementation of a Karate Driver for Playwright.
 *
 * Unlike the original PlaywrightDriver living in karate-core which was based on
 * the internal wire protocol, this one uses the public Playwright APIs. To use
 * it, make sure the karate-playwright dependency is added in your pom.xml, and
 * located before karate-core.
 *
 * It supports: - xpath, css, wildcard locators as well as friendly locators
 * (through custom locators which we believe are better suited to Karate than
 * the 'right-of' pseudo selectors offered by PW) - headless tests - http
 * requests intercepting (basic support, only urlPatterns are matched) - and of
 * course all the browsers supported by Playwright.
 *
 * This driver will start up a Playwright engine unless the playwrightUrl is
 * specified, in which case the driver will try to connect to that url.
 *
 * A couple of additional options may be specified in the playwrightOptions
 * property: - installBrowsers ("true"/"false"): whether PW will automatically
 * download and install the browsers - channel (e.g. "chrome"): for the
 * "chromium" browserType, Playwright allows us to pick the underlying engine.
 *
 * The following points are not 100% identical to the other Drivers - Cookies
 * are supported but a domain/path or url key is mandatory - Retries are
 * supported but, per doc, drivers should wait the specified
 * <pre>interval</pre> number of milliseconds before retrying. This driver will
 * however wait <i>at most</i><pre>interval</pre> milliseconds, but it leverages
 * Playwright's auto-wait/auto-retry to return as soon as the element is
 * available and won't wait for the full specified interval.But ig(count: 3,
 * interval: 3000 milliseconds) means try three times, and wait for 3 seconds
 * before the next re-try attempt. In fact, if slowDiv takes 2 seconds to load,
 * retry(3, 1500).click('#slowDiv') will return in roughly 2s. So will retry(2,
 * 2000), and retry(5, 800). Of course, retry(3, 500) will fail.
 */
/*
 * Possible improvements:
 * - add option to enable tracing
 * - take advantage of PW's multi browser capability.
 * 
 */
public class PlaywrightDriver implements Driver {

    // Revert back to options.timeout
    private static final Integer DEFAULT_TIMEOUT = null;

    private static final String FRIENDLY_ENGINE = "{\n"
            + "  queryAll(root,args) {\n"
            + "    function retain_right(rootRect, itemRect) {\n"
            + "       return itemRect.x >= (rootRect.x + rootRect.width) && itemRect.y <= (rootRect.y + rootRect.height) && (itemRect.y + itemRect.height) >=rootRect.y;\n"
            + "    }\n"
            + "    function retain_left(rootRect, itemRect) {\n"
            + "       return (itemRect.x + itemRect.width) <= rootRect.x && itemRect.y <= (rootRect.y + rootRect.height) && (itemRect.y + itemRect.height) >=rootRect.y;\n"
            + "    }\n"
            + "    function retain_below(rootRect, itemRect) {\n"
            + "       return itemRect.y >= (rootRect.y + rootRect.height) && itemRect.x <= (rootRect.x + rootRect.width) && (itemRect.x + itemRect.width) >=rootRect.x;\n"
            + "    }\n"
            + "    function retain_above(rootRect, itemRect) {\n"
            + "       return (itemRect.y + itemRect.height) <= rootRect.y && itemRect.x <= (rootRect.x + rootRect.width) && (itemRect.x + itemRect.width) >=rootRect.x;\n"
            + "    }\n"
            + "    function retain_near(rootRect, itemRect) {\n"
            + "       return true;\n"
            + "    }\n"
            + "    function items_list(selector) {\n"
            + "       if (selector.startsWith('/') || selector.startsWith('xpath=')) {\n"
            + "            let items_list = [];\n"
            + "            let query = document.evaluate(argsParts[1],document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);\n"
            + "            for (let i = 0; i<query.snapshotLength; i++) {\n"
            + "                items_list.push(query.snapshotItem(i));\n"
            + "            }\n"
            + "            return items_list;\n"
            + "       } else {\n"
            + "            return Array.from(document.querySelectorAll(selector));\n"
            + "       }\n"
            + "    }\n"
            + "    let rootRect = root.getBoundingClientRect();\n"
            + "    let argsParts = args.split(':');\n"
            + "    let itemsByDistance = new Map();\n"
            + "    let items = items_list(argsParts[1]);\n"
            + "    for (let i = 0; i<items.length; i++) {\n"
            + "      let item = items[i];\n"
            + "      let itemRect = item.getBoundingClientRect();\n"
            + "      if (eval('retain_'+argsParts[0])(rootRect, itemRect)){\n"
            + // distance between root's center and item's. This is actually the squared distance but the actual values do not matter, as long as they are comparable with each other, which squared distances are.
            "        let distance = Math.pow(rootRect.x+rootRect.width/2-(itemRect.x+itemRect.width/2), 2) + Math.pow(rootRect.y+rootRect.height/2-(itemRect.y+itemRect.height/2), 2);\n"
            + "        itemsByDistance.set(item, distance);\n"
            + "      }\n"
            + "    }\n"
            + "    return [...itemsByDistance].sort((a, b) => a[1] - b[1]).map(item => item[0]);\n"
            + "  }\n"
            + "}";

    final PlaywrightDriverOptions options;
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext browserContext;
    Page page;

    private FrameTrait root;

    private boolean terminated = false;
    private String dialogText;

    double retryTimeout;

    public interface PlaywrightDriverFactory<T extends PlaywrightDriver> {

        T create(PlaywrightDriverOptions options, Browser browser, Playwright playwright);
    }

    public static PlaywrightDriver start(Map<String, Object> map, ScenarioRuntime sr) {
        return start(map, sr, PlaywrightDriver::new);
    }

    public static <T extends PlaywrightDriver> T start(Map<String, Object> map, ScenarioRuntime sr, PlaywrightDriverFactory<T> factory) {

        PlaywrightDriverOptions options = new PlaywrightDriverOptions(map, sr, 4444, "playwright");

        Map<String, Object> pwOptions = options.playwrightOptions == null ? Collections.emptyMap() : options.playwrightOptions;
        String browserTypeOption = (String) pwOptions.getOrDefault("browserType", "chromium");

        Browser browser;
        if (Boolean.valueOf(pwOptions.getOrDefault("installBrowsers", "true") == Boolean.FALSE)) {
            // ensureDriverInstalled is called by Playwright.create, but the installBrowsers is forced to true.
            // We call it here with a falsy installBrowsers, the singleton will be created and subsequent call from Playwright.create will have no effect
            com.microsoft.playwright.impl.driver.Driver.ensureDriverInstalled(Collections.emptyMap(), false);
        }
        try {
            Playwright playwright = Playwright.create();
            playwright.selectors().register("friendly", FRIENDLY_ENGINE);
            Method browserTypeMethod = Playwright.class.getDeclaredMethod(browserTypeOption);
            BrowserType browserType = (BrowserType) browserTypeMethod.invoke(playwright);
            if (options.start) {
                browser = browserType.launch(new BrowserType.LaunchOptions()
                        .setHeadless(options.headless)
                        .setChannel((String) pwOptions.getOrDefault("channel", "chromium")));
            } else {

                String playwrightUrl = options.playwrightUrl;
                if (playwrightUrl == null) {
                    throw new RuntimeException("playwrightUrl is mandatory if start == false");
                }
                browser = browserType.connect(playwrightUrl);
            }
            return factory.create(options, browser, playwright);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public PlaywrightDriver(PlaywrightDriverOptions options, Browser browser, Playwright playwright) {
        this.options = options;
        this.playwright = playwright;
        options.setDriver(this);
        this.browser = browser;
        this.browserContext = browser.newContext();
        this.timeout(null);
        setPage(browserContext.newPage());
    }

    private void setPage(Page page) {
        this.page = page;
        this.root = FrameTrait.of(page);
    }

    @Override
    public void quit() {
        if (!terminated) {
            terminated = true;
            browserContext.close();
            browser.close();
            playwright.close();
        }
    }

    @Override
    public String getDialogText() {
        return dialogText;
    }

    // private Locator locator(String locator) {
    //     return this.locator.locatorFor(locator);
    // }
    @Override
    public void dialog(boolean accept) {
        dialog(accept, Dialog::accept);
    }

    @Override
    public void dialog(boolean accept, String input) {
        dialog(accept, dialog -> dialog.accept(input));
    }

    private void dialog(boolean accept, Consumer<Dialog> onAccept) {
        page.onDialog(dialog -> {
            if ("alert".equals(dialog.type()) || !accept) {
                dialog.dismiss();
                dialogText = null;
            } else {
                onAccept.accept(dialog);
                this.dialogText = dialog.message();
            }
        });
    }

    private PlaywrightToken ofRoot(String locator) {
        return PlaywrightToken.root(this, locator);
    }

    @Override
    public Object script(String expression) {
        return page.evaluate(toJsExpression(expression));
    }

    @Override
    public Object script(String locator, String expression) {
        return script(rootLocator(locator).first(), expression);
    }

    @Override
    public List<Object> scriptAll(String locator, String expression) {
        return scriptAll(rootLocator(locator), expression);
    }

    @Override
    public Element waitFor(String locator) {
        return new PlaywrightElement(this, ofRoot(locator)).waitFor();
    }

    @Override
    public Element waitForAny(String locator1, String locator2) {
        return waitForAny(new String[]{locator1, locator2});
    }

    @Override
    public Element waitForAny(String[] locators) {
        List<Locator> pwLocators = Arrays.stream(locators).map(token -> rootLocator(token)).collect(Collectors.toList());

        Locator orLocators = pwLocators.get(0);
        for (int i = 1; i < pwLocators.size(); i++) {
            orLocators = orLocators.or(pwLocators.get(i));
        }
        orLocators.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

        // Find which locator is available, and return it.
        // This is on par with waitForAny specs. However, I wonder if just returning orLocators and let PW work out which one is available in the subsequent calls (click, ...) would work.
        // Im not completely sold it would ( and that''s without even touching on how to create an element from a locator...)
        for (int i = 0; i < pwLocators.size(); i++) {
            if (pwLocators.get(i).isVisible()) {
                return new PlaywrightElement(this, ofRoot(locators[i]));
            }
        }

        throw new IllegalStateException();
    }

    @Override
    public Element waitForEnabled(String locator) {
        // Per https://playwright.dev/java/docs/actionability, Playwright will auto-wait for enabled when the next action (click, ...) is invoked.
        // Nothing to do here.
        return new PlaywrightElement(this, ofRoot(locator));
    }

    @Override
    public Element waitForText(String locator, String text) {
        return new PlaywrightElement(this, ofRoot(locator)).waitForText(text);
    }

    @Override
    public Element waitUntil(String locator, String expression) {
        return new PlaywrightElement(this, ofRoot(locator)).waitUntil(expression);
    }

    public List<Element> waitForResultCount(String locator, int count) {
        Locator allLocators = rootLocator(locator);
        try {
            assertThat(allLocators).hasCount(count);
            return locateAll(locator);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Object> waitForResultCount(String locator, int count, String expression) {
        String jsExpression = toJsExpression(expression);
        List<Element> elements = waitForResultCount(locator, count);
        return elements.stream().map(element -> element.script(jsExpression)).collect(Collectors.toList());
    }

    @Override
    public Element focus(String locator) {
        return new PlaywrightElement(this, ofRoot(locator)).focus();
    }

    @Override
    public Element clear(String locator) {
        return new PlaywrightElement(this, ofRoot(locator)).clear();
    }

    @Override
    public Element click(String locator) {
        return new PlaywrightElement(this, ofRoot(locator)).click();
    }

    @Override
    public Element value(String locator, String value) {
        PlaywrightElement element = new PlaywrightElement(this, ofRoot(locator));
        element.setValue(value);
        return element;
    }

    @Override
    public Element input(String locator, String value) {
        return new PlaywrightElement(this, ofRoot(locator)).input(value);
    }

    @Override
    public Element input(String locator, String value, int delay) {
        String[] array = value.chars().mapToObj(ch -> String.valueOf((char) ch)).toArray(String[]::new);
        return new PlaywrightElement(this, ofRoot(locator)).input(array, delay);
    }

    public Element input(String locator, String[] values, int delay) {
        return new PlaywrightElement(this, ofRoot(locator)).input(values, delay);
    }

    @Override
    public Element select(String locator, String text) {
        return new PlaywrightElement(this, ofRoot(locator)).select(text);
    }

    @Override
    public Element select(String locator, int index) {
        return new PlaywrightElement(this, ofRoot(locator)).select(index);
    }

    @Override
    public String html(String locator) {
        return (String) rootLocator(locator).evaluate("el => el.outerHTML", null);
    }

    @Override
    public String text(String locator) {
        return rootLocator(locator).textContent();
    }

    @Override
    public String value(String locator) {
        return rootLocator(locator).inputValue(new Locator.InputValueOptions());
    }

    @Override
    public String attribute(String locator, String name) {
        return rootLocator(locator).getAttribute(name);
    }

    @Override
    public String property(String locator, String name) {
        return String.valueOf(rootLocator(locator).elementHandle().getProperty(name));
    }

    @Override
    public boolean enabled(String locator) {
        return rootLocator(locator).isEnabled();
    }

    public boolean exists(String locator) {
        return exists(rootLocator(locator));
    }

    boolean exists(Locator locator) {
        // Implemented using isVisible, which seems to be the general view per https://stackoverflow.com/questions/64784781/how-to-check-if-an-element-exists-on-the-page-in-playwright-js
        // although, if isAttached was available, it probably would have made more sense.
        return locator.isVisible();
    }

    @Override
    public Finder rightOf(String locator) {
        return new PlaywrightFinder(this, ofRoot(locator), "right-of");
    }

    @Override
    public Finder leftOf(String locator) {
        return new PlaywrightFinder(this, ofRoot(locator), "left-of");
    }

    @Override
    public Finder near(String locator) {
        return new PlaywrightFinder(this, ofRoot(locator), "near");
    }

    @Override
    public Finder above(String locator) {
        return new PlaywrightFinder(this, ofRoot(locator), "above");
    }

    @Override
    public Finder below(String locator) {
        return new PlaywrightFinder(this, ofRoot(locator), "below");
    }

    public Element highlight(String locator, int millis) {
        // todo millis not taken into account.
        return new PlaywrightElement(this, ofRoot(locator)).highlight();
    }

    public void highlightAll(String locator, int millis) {
        // todo millis not taken into account.
        locateAll(locator).forEach(Element::highlight);
    }

    ///////////////////////////////////////////////
    // Locate and its lenient counterpart optional
    //////////////////////////////////////////////
    @Override
    public List<Element> locateAll(String locator) {
        return PlaywrightElement.locateAll(this, ofRoot(locator));

    }

    @Override
    public Element locate(String locator) {
        return PlaywrightElement.locate(this, ofRoot(locator).first());
    }

    @Override
    public Element optional(String locator) {
        return PlaywrightElement.optional(this, ofRoot(locator).first());
    }

    ////////////////////////////////////////////////////////////
    // Position, scroll, screenshot locator based-operations
    ///////////////////////////////////////////////////////////
    @Override
    public Map<String, Object> position(String locator) {
        BoundingBox boundingBox = rootLocator(locator).boundingBox();
        return asCoordinatesMap(boundingBox);
    }

    static Map<String, Object> asCoordinatesMap(BoundingBox boundingBox) {
        return asCoordinatesMap(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
    }

    private static Map<String, Object> asCoordinatesMap(double x, double y, double width, double height) {
        Map<String, Object> position = new HashMap<>();
        position.put("x", x);
        position.put("y", y);
        position.put("width", Math.round(width));
        position.put("height", Math.round(height));
        return position;
    }

    @Override
    public Map<String, Object> position(String locator, boolean relative) {
        if (!relative) {
            return position(locator);
        }
        return (Map<String, Object>) script(DriverOptions.getPositionJs(locator));
    }

    @Override
    public Element scroll(String locator) {
        return new PlaywrightElement(this, ofRoot(locator)).scroll();
    }

    @Override
    public byte[] screenshot(String locator, boolean embed) {
        byte[] screenshot = this.screenshot(rootLocator(locator));
        if (embed) {
            getRuntime().embed(screenshot, ResourceType.PNG);
        }
        return screenshot;
    }

    /////////////////////////////////////////////////////
    // Page based-operations
    /////////////////////////////////////////////////////
    @Override
    public void activate() {
        page.bringToFront();
    }

    @Override
    public void refresh() {
        page.reload();
    }

    @Override
    public void reload() {
        // https://playwright.dev/java/docs/api/class-page#page-route Enabling routing disables http cache(?)
        page.route("*", Route::resume);
        page.reload();
        page.unroute("*");
    }

    @Override
    public void back() {
        page.goBack();
    }

    @Override
    public void forward() {
        page.goForward();
    }

    @Override
    public void maximize() {

    }

    @Override
    public void minimize() {

    }

    @Override
    public void fullscreen() {
    }

    @Override
    public void close() {
        page.close();
    }

    @Override
    public String getUrl() {
        return page.url();
    }

    @Override
    public void setUrl(String url) {
        page.navigate(url);
    }

    @Override
    public Map<String, Object> getDimensions() {
        ViewportSize viewportSize = page.viewportSize();
        return asCoordinatesMap(0, 0, viewportSize.width, viewportSize.height);
    }

    @Override
    public void setDimensions(Map<String, Object> map) {
        page.setViewportSize((Integer) map.get("width"), (Integer) map.get("height"));
    }

    @Override
    public String getTitle() {
        // works both with frame and page.
        return root.getTitle();
    }

    @Override
    public byte[] screenshot(boolean embed) {
        byte[] screenshot = page.screenshot();
        if (embed) {
            getRuntime().embed(screenshot, ResourceType.PNG);
        }
        return screenshot;
    }

    @Override
    public byte[] pdf(Map<String, Object> options) {
        return page.pdf(new Page.PdfOptions().setLandscape("landscape".equalsIgnoreCase((String) options.get("orientation"))));
    }

    @Override
    public boolean waitUntil(String expression) {
        try {
            waitForFunction(expression, null);
            return true;
        } catch (Exception e) {
            options.driverLogger.warn("waitUntil evaluate failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String waitForUrl(String url) {
        page.waitForURL("**" + url);
        return getUrl();
    }

    ///////////////////////////////////////////////////////
    // Page(s), cookies, timeouts and other context-based operations
    // (also contains swtichFrames method altough arguably they shoul be somewhere else)
    ///////////////////////////////////////////////////////
    // Sets PWs NAVIGATION timeout.
    // See also retryTimeout
    @Override
    public Driver timeout(Integer millis) {
        browserContext.setDefaultNavigationTimeout(millis == DEFAULT_TIMEOUT ? options.timeout : millis.doubleValue());
        return this;
    }

    // sets the value used to implement Karate's retries.
    // It will be passed to the timeout field of action's (click, input, select, ...) options.
    // It is not possible to completely disable PW's autowait - and in fact we probably dont want to do that as it causes lots of errors -
    // so we (arbitrarily) set a default of 100ms
    void retryTimeout(Double millis) {
        this.retryTimeout = millis == null ? 100 : millis;
    }

    @Override
    public Driver timeout() {
        return timeout(DEFAULT_TIMEOUT);
    }

    @Override
    public void switchPage(String titleOrUrl) {
        browserContext.waitForCondition(() -> findPage(titleOrUrl).isPresent());
        findPage(titleOrUrl).ifPresent(this::setPage);
    }

    private Optional<Page> findPage(String titleOrUrl) {
        return browserContext.pages().stream().filter(candidate -> candidate.url().contains(titleOrUrl) || candidate.title().contains(titleOrUrl)).findAny();
    }

    @Override
    public void switchPage(int index) {
        setPage(browserContext.pages().get(index));
    }

    @Override
    public void switchFrame(int index) {
        if (index == -1) {
            setPage(page);
        } else {
            int frameIndex = index + 1; // frame[0]is the main frame
            this.root = FrameTrait.of(page.frames().get(frameIndex));
        }
    }

    @Override
    public void switchFrame(String locator) {
        if (locator == null) {
            setPage(page);
        } else {
            this.root = FrameTrait.of(this.root.frameLocator(locator));
        }
    }

    void switchTo(Locator locator) {
        this.root = FrameTrait.of(locator.frameLocator(":root"));
    }

    @Override
    public List<String> getPages() {
        return browserContext.pages().stream().map(Page::toString).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> cookie(String name) {
        return browserContext.cookies().stream().filter(cookie -> name.equals(cookie.name))
                .findAny()
                .map(this::asCookieMap)
                // TODO: what is the expected behavior if no cookie found?
                .orElse(Collections.emptyMap());
    }

    @Override
    public void cookie(Map<String, Object> cookieMap) {
        browserContext.addCookies(Arrays.asList(
                new Cookie((String) cookieMap.get("name"), (String) cookieMap.get("value"))
                        .setDomain((String) cookieMap.get("domain"))
                        .setPath((String) cookieMap.get("path"))
                        .setUrl((String) cookieMap.get("url")))
        );
    }

    @Override
    public void deleteCookie(String name) {
        List<Cookie> cookies = browserContext.cookies();
        browserContext.clearCookies();
        browserContext.addCookies(cookies.stream().filter(cookie -> !cookie.name.equals(name)).collect(Collectors.toList()));
    }

    @Override
    public void clearCookies() {
        browserContext.clearCookies();
    }

    @Override
    public List<Map> getCookies() {
        return browserContext.cookies().stream().map(this::asCookieMap).collect(Collectors.toList());
    }

    private Map<String, Object> asCookieMap(Cookie cookie) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", cookie.name);
        map.put("value", cookie.value);
        map.put("url", cookie.url);
        map.put("domain", cookie.domain);
        map.put("path", cookie.path);
        return map;
    }

    public void intercept(Value value) {
        Map<String, Object> config = (Map) JsValue.toJava(value);
        config = new Variable(config).getValue();
        intercept(config);
    }

    public void intercept(Map<String, Object> config) {
        List<Map<String, String>> patterns = (List<Map<String, String>>) Objects.requireNonNull(config.get("patterns"), "missing array argument 'patterns'");
        List<Pattern> urlPatterns = patterns.stream().map(pattern -> Pattern.compile(pattern.get("urlPattern").replace("*", ".*").replace("?", ".?"))).collect(Collectors.toList());
        String mock = (String) Objects.requireNonNull(config.get("mock"), "missing argument 'mock'");

        Object o = getRuntime().engine.fileReader.readFile(mock);
        if (!(o instanceof FeatureCall)) {
            throw new IllegalArgumentException("'mock' is not a feature file: " + mock);
        }
        FeatureCall fc = (FeatureCall) o;
        MockHandler mockHandler = new MockHandler(fc.feature);
        page.route(url -> matches(url, urlPatterns), route -> {
            HttpRequest karateRequest = new HttpRequest();
            karateRequest.setUrl(route.request().url());
            karateRequest.setMethod(route.request().method());
            karateRequest.setBody(route.request().postDataBuffer());
            route.request().headers().forEach(karateRequest::putHeader);
            Response karateResponse = mockHandler.handle(karateRequest.toRequest());
            Map<String, String> responseHeaders = new HashMap<>();
            karateResponse.getHeaders().forEach((k, v) -> responseHeaders.put(k, v.get(0)));
            route.fulfill(new Route.FulfillOptions().setStatus(karateResponse.getStatus()).setBodyBytes(karateResponse.getBody()).setHeaders(responseHeaders));
        });
    }

    private boolean matches(String url, List<Pattern> urlPatterns) {
        for (Pattern urlPattern : urlPatterns) {
            if (urlPattern.matcher(url).matches()) {
                return true;
            }
        }
        return false;
    }

    /////////////////////////////////////////////////
    // Chaining stuff
    /////////////////////////////////////////////////
    private Driver driverProxy(InvocationHandler h) {
        return (Driver) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{Driver.class}, h);
    }

    @Override
    public Driver submit() {
        InvocationHandler h = InvocationHandlers.submitHandler(this, getWaitingForPage());
        return driverProxy(h);
    }

    @Override
    public Driver retry(Integer count, Integer interval) {
        return driverProxy(InvocationHandlers.retryHandler(this, count, interval, options.driverLogger, this));
    }

    /////////////////////////////////////////////////
    // Mouse
    /////////////////////////////////////////////////
    @Override
    public Mouse mouse() {
        return mouse(":root");
    }

    @Override
    public Mouse mouse(String locator) {
        return new PlaywrightMouse(this, ofRoot(locator));
    }

    @Override
    public Mouse mouse(Number x, Number y) {
        return mouse().move(x, y);
    }

    /////////////////////////////////////////////////
    // Driver APIs that probably should not be public
    ////////////////////////////////////////////////
    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    @Override
    public void actions(List<Map<String, Object>> actions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object elementId(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List elementIds(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object waitUntil(Supplier<Object> condition) {
        throw new UnsupportedOperationException();
    }

    /////////////////////////////////////////////////
    // PlaywrightDriver protected APIs
    ////////////////////////////////////////////////
    byte[] screenshot(Locator locator) {
        return locator.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
    }

    // Cannot call Thread.sleep or else no messages will be sent.
    void sleep(int millis) {
        page.waitForTimeout(millis);
    }

    WaitForPageLoaded getWaitingForPage() {
        return new WaitForPageLoaded(page, browserContext);
    }

    com.microsoft.playwright.Mouse getMouse() {
        return page.mouse();
    }

    private static String toJsExpression(String expression) {
        return expression.startsWith("_.") ? ("el => el." + expression.substring(2))
                : expression.startsWith("!_.") ? ("el => !el." + expression.substring(3))
                : expression;
    }

    void waitForFunction(String expression, ElementHandle elementHandle) {
        page.waitForFunction(toJsExpression(expression), elementHandle);
    }

    Object script(Locator locator, String karateExpression) {
        return locator.evaluate(toJsExpression(karateExpression));
    }

    List<Object> scriptAll(Locator locator, String expression) {
        String jsExpression = toJsExpression(expression);
        return locator.all().stream().map(loc -> loc.evaluate(jsExpression)).collect(Collectors.toList());
    }

    Locator rootLocator(String locator) {
        return this.root.locator(locator);
    }

    /**
     * <p>
     * A Frame has a title and can create {@link FrameLocator}s as well as
     * regular {@link Locator}s. So have other Frame-like classes.
     * </p>
     * This class acts as a common interface for different Playwright classes
     * providing these capabilities.
     *
     */
    public static interface FrameTrait {

        public String getTitle();

        public FrameLocator frameLocator(String token);

        public Locator locator(String token);

        public static FrameTrait of(Page page) {
            return new FrameTrait() {
                @Override
                public String getTitle() {
                    return page.title();
                }

                @Override
                public FrameLocator frameLocator(String token) {
                    return page.frameLocator(token);
                }

                @Override
                public Locator locator(String token) {
                    return page.locator(token);
                }
            };
        }

        public static FrameTrait of(Frame frame) {
            return new FrameTrait() {
                @Override
                public String getTitle() {
                    return frame.title();
                }

                @Override
                public FrameLocator frameLocator(String token) {
                    return frame.frameLocator(token);
                }

                @Override
                public Locator locator(String token) {
                    return frame.locator(token);
                }
            };
        }

        public static FrameTrait of(FrameLocator frameLocator) {
            return new FrameTrait() {
                @Override
                public String getTitle() {
                    return (String) frameLocator.locator(":root").evaluate("document.title");
                }

                @Override
                public FrameLocator frameLocator(String token) {
                    return frameLocator.frameLocator(token);
                }

                @Override
                public Locator locator(String token) {
                    return frameLocator.locator(token);
                }
            };
        }
    }

    public static class WaitForPageLoaded implements Runnable, Closeable {

        private final Page page;

        private final BrowserContext browserContext;
        private Consumer<Page> listener;

        public WaitForPageLoaded(Page page, BrowserContext browserContext) {
            this.page = page;
            this.browserContext = browserContext;
        }

        @Override
        public void run() {
            // from the doc, my understanding is that submit() applies when navigating within the same page rather than in a new page.
            // For the latter, the very handy browserContext.waitForPage method could be used.
            // (which unfortunately can not be used to implement switchPage since it requires some chaining).
            // For the former, waitForCondition is used, waiting for a DOMContentLoaded notification.
            AtomicBoolean pageIsLoaded = new AtomicBoolean(false);
            listener = page -> pageIsLoaded.set(true);
            page.onDOMContentLoaded(listener);

            browserContext.waitForCondition(pageIsLoaded::get);
        }

        public void close() {
            page.offDOMContentLoaded(listener);
        }
    }
    
}

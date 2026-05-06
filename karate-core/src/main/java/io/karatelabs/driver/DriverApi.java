package io.karatelabs.driver;

import io.karatelabs.js.Args;
import io.karatelabs.js.Context;
import io.karatelabs.js.Engine;
import io.karatelabs.js.JavaCallable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class DriverApi {

    // Element actions
    public static final String CLICK = "click";
    public static final String INPUT = "input";
    public static final String CLEAR = "clear";
    public static final String FOCUS = "focus";
    public static final String SCROLL = "scroll";
    public static final String HIGHLIGHT = "highlight";
    public static final String HIGHLIGHT_ALL = "highlightAll";
    public static final String SELECT = "select";
    public static final String SUBMIT = "submit";

    // Element state
    public static final String TEXT = "text";
    public static final String HTML = "html";
    public static final String VALUE = "value";
    public static final String ATTRIBUTE = "attribute";
    public static final String PROPERTY = "property";
    public static final String EXISTS = "exists";
    public static final String ENABLED = "enabled";
    public static final String POSITION = "position";

    // Locators
    public static final String LOCATE = "locate";
    public static final String LOCATE_ALL = "locateAll";
    public static final String OPTIONAL = "optional";

    // Wait methods
    public static final String WAIT_FOR = "waitFor";
    public static final String WAIT_FOR_ANY = "waitForAny";
    public static final String WAIT_FOR_TEXT = "waitForText";
    public static final String WAIT_FOR_ENABLED = "waitForEnabled";
    public static final String WAIT_FOR_URL = "waitForUrl";
    public static final String WAIT_UNTIL = "waitUntil";
    public static final String WAIT_FOR_RESULT_COUNT = "waitForResultCount";

    // Frame/Page
    public static final String SWITCH_FRAME = "switchFrame";
    public static final String SWITCH_PAGE = "switchPage";
    public static final String GET_PAGES = "getPages";

    // Script
    public static final String SCRIPT = "script";
    public static final String SCRIPT_ALL = "scriptAll";

    // Navigation
    public static final String REFRESH = "refresh";
    public static final String RELOAD = "reload";
    public static final String BACK = "back";
    public static final String FORWARD = "forward";

    // Window
    public static final String MAXIMIZE = "maximize";
    public static final String MINIMIZE = "minimize";
    public static final String FULLSCREEN = "fullscreen";
    public static final String ACTIVATE = "activate";

    // Lifecycle
    public static final String CLOSE = "close";
    public static final String QUIT = "quit";

    // Screenshot
    public static final String SCREENSHOT = "screenshot";

    // PDF
    public static final String PDF = "pdf";

    // Cookies
    public static final String COOKIE = "cookie";
    public static final String COOKIES_SET = "setCookies";
    public static final String CLEAR_COOKIES = "clearCookies";
    public static final String DELETE_COOKIE = "deleteCookie";

    // Intercept
    public static final String INTERCEPT = "intercept";

    // Dialog
    public static final String DIALOG = "dialog";
    public static final String ON_DIALOG = "onDialog";
    public static final String DIALOG_TEXT = "dialogText";

    // Input
    public static final String MOUSE = "mouse";
    public static final String KEYS = "keys";

    // Positional finders
    public static final String RIGHT_OF = "rightOf";
    public static final String LEFT_OF = "leftOf";
    public static final String ABOVE = "above";
    public static final String BELOW = "below";
    public static final String NEAR = "near";

    // Retry / timing
    public static final String RETRY = "retry";
    public static final String TIMEOUT = "timeout";

    // Properties
    public static final String URL = "url";
    public static final String TITLE = "title";
    public static final String COOKIES = "cookies";
    public static final String DIMENSIONS = "dimensions";

    // Collection of all bindable method names
    public static final List<String> BOUND_METHODS = List.of(
            CLICK, INPUT, CLEAR, FOCUS, SCROLL, HIGHLIGHT, HIGHLIGHT_ALL, SELECT, SUBMIT,
            TEXT, HTML, VALUE, ATTRIBUTE, PROPERTY, EXISTS, ENABLED, POSITION,
            LOCATE, LOCATE_ALL, OPTIONAL,
            WAIT_FOR, WAIT_FOR_ANY, WAIT_FOR_TEXT, WAIT_FOR_ENABLED, WAIT_FOR_URL, WAIT_UNTIL, WAIT_FOR_RESULT_COUNT,
            SWITCH_FRAME, SWITCH_PAGE, GET_PAGES,
            SCRIPT, SCRIPT_ALL,
            REFRESH, RELOAD, BACK, FORWARD,
            MAXIMIZE, MINIMIZE, FULLSCREEN, ACTIVATE,
            CLOSE, QUIT,
            SCREENSHOT, PDF,
            COOKIE, COOKIES_SET, CLEAR_COOKIES, DELETE_COOKIE,
            INTERCEPT,
            DIALOG, ON_DIALOG,
            MOUSE, KEYS,
            RIGHT_OF, LEFT_OF, ABOVE, BELOW, NEAR,
            RETRY, TIMEOUT
    );

    /**
     * Adapt a JS/Java callable argument to a {@code Supplier<Object>}.
     * <p>Karate treats inline JS lambdas as first-class Java callables — the
     * same pattern that powers {@code karate.filter(list, x => ...)}.
     * This helper lets driver bindings route a callable argument to the
     * {@code Supplier}-taking overload of a method (polled locally in
     * karate-js) rather than coercing it to a string and shipping it off to
     * the browser, where its karate-js scope wouldn't exist.
     *
     * @return a {@code Supplier} wrapping the callable, or {@code null} when
     * the argument is not a recognised callable (caller should fall through
     * to the {@code String} overload).
     */
    public static Supplier<Object> asSupplier(Object arg, Context ctx) {
        if (arg instanceof JavaCallable jc) {
            return () -> jc.call(ctx, new Object[0]);
        }
        if (arg instanceof Supplier<?> s) {
            return () -> (Object) s.get();
        }
        if (arg instanceof Callable<?> c) {
            return () -> {
                try {
                    return c.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
        return null;
    }

    /**
     * Bind driver action methods as global functions.
     * This allows writing `* click('#button')` instead of `* driver.click('#button')`.
     * Reuses JavaCallables from Driver.jsGet() for consistency.
     */
    public static void bindJsHelpers(Engine engine, Driver driver) {
        // Bind all methods by retrieving existing JavaCallables from driver
        for (String methodName : BOUND_METHODS) {
            Object callable = driver.jsGet(methodName);
            if (callable != null) {
                engine.putRootBinding(methodName, callable);
            }
        }

        // Special bindings that don't come from Driver
        engine.putRootBinding("Key", new io.karatelabs.js.JavaType(Keys.class));
        engine.putRootBinding("delay", Args.invoke(args -> {
            int millis = args.length > 0 ? ((Number) args[0]).intValue() : 0;
            if (millis > 0) {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        }));
    }

}

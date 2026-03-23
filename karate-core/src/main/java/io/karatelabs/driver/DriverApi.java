package io.karatelabs.driver;

import io.karatelabs.js.Args;
import io.karatelabs.js.Engine;

import java.util.List;

public class DriverApi {

    // Element actions
    public static final String CLICK = "click";
    public static final String INPUT = "input";
    public static final String CLEAR = "clear";
    public static final String FOCUS = "focus";
    public static final String SCROLL = "scroll";
    public static final String HIGHLIGHT = "highlight";
    public static final String SELECT = "select";

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
    public static final String WAIT_FOR_TEXT = "waitForText";
    public static final String WAIT_FOR_ENABLED = "waitForEnabled";
    public static final String WAIT_FOR_URL = "waitForUrl";
    public static final String WAIT_UNTIL = "waitUntil";

    // Frame/Page
    public static final String SWITCH_FRAME = "switchFrame";
    public static final String SWITCH_PAGE = "switchPage";
    public static final String GET_PAGES = "getPages";

    // Script
    public static final String SCRIPT = "script";
    public static final String SCRIPT_ALL = "scriptAll";

    // Navigation
    public static final String REFRESH = "refresh";
    public static final String BACK = "back";
    public static final String FORWARD = "forward";

    // Screenshot
    public static final String SCREENSHOT = "screenshot";

    // Cookies
    public static final String COOKIE = "cookie";
    public static final String CLEAR_COOKIES = "clearCookies";
    public static final String DELETE_COOKIE = "deleteCookie";

    // Dialog
    public static final String DIALOG = "dialog";
    public static final String ON_DIALOG = "onDialog";

    // Input
    public static final String MOUSE = "mouse";
    public static final String KEYS = "keys";

    // Properties
    public static final String URL = "url";
    public static final String TITLE = "title";
    public static final String COOKIES = "cookies";
    public static final String DIMENSIONS = "dimensions";

    // Collection of all bindable method names
    public static final List<String> BOUND_METHODS = List.of(
            CLICK, INPUT, CLEAR, FOCUS, SCROLL, HIGHLIGHT, SELECT,
            TEXT, HTML, VALUE, ATTRIBUTE, EXISTS, ENABLED, POSITION,
            LOCATE, LOCATE_ALL, OPTIONAL,
            WAIT_FOR, WAIT_FOR_TEXT, WAIT_FOR_ENABLED, WAIT_FOR_URL, WAIT_UNTIL,
            SWITCH_FRAME, SWITCH_PAGE, GET_PAGES,
            SCRIPT, SCRIPT_ALL,
            REFRESH, BACK, FORWARD,
            SCREENSHOT,
            COOKIE, CLEAR_COOKIES, DELETE_COOKIE,
            DIALOG, ON_DIALOG,
            MOUSE, KEYS
    );

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

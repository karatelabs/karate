# Karate Driver
## UI Test Automation Made `Simple.`

## Introduction
> This is new, and this first version 0.9.X should be considered experimental.

Especially after [the Gherkin parser and execution engine were re-written from the ground-up](https://github.com/intuit/karate/issues/444#issuecomment-406877530), Karate is arguably a mature framework that elegantly solves quite a few test-automation engineering challenges - with capabilities such as [parallel execution](https://twitter.com/KarateDSL/status/1049321708241317888), [data-driven testing](https://github.com/intuit/karate#data-driven-tests), [environment-switching](https://github.com/intuit/karate#switching-the-environment), [powerful assertions](https://github.com/intuit/karate#contains-short-cuts), and an [innovative UI for debugging](https://twitter.com/KarateDSL/status/1065602097591156736).

Which led us to think, what if we could add UI automation without disturbing the core HTTP API testing capabilities. So we gave it a go, and we are releasing the results so far as this experimental version.

Please do note: this is work in progress and all actions needed for test-automation may not be in-place. But we hope that releasing this sooner would result in more users trying this in a variety of environments. And that they provide valuable feedback and even contribute code where possible.

We know too well that UI automation is hard to get right and suffers from 2 big challenges, what we like to call the "*flaky test*" problem and the "*wait for UI element*" problem.

With the help of the community, we would like to try valiantly - to see if we can get close to as ideal a state a possible. So wish us luck !

## Capabilities

* Direct-to-Chrome automation using the [DevTools protocol](https://chromedevtools.github.io/devtools-protocol/)
* [W3C WebDriver](https://w3c.github.io/webdriver/) support
* [Cross-Browser support](https://twitter.com/ptrthomas/status/1048260573513666560) including [Microsoft Edge on Windows](https://twitter.com/ptrthomas/status/1046459965668388866) and [Safari on Mac](https://twitter.com/ptrthomas/status/1047152170468954112)
* WebDriver support without any intermediate server
* Windows [Desktop application automation](https://twitter.com/KarateDSL/status/1052432964804640768) using the Microsoft [WinAppDriver](https://github.com/Microsoft/WinAppDriver)
* Android and iOS mobile support via [Appium](http://appium.io), see [details](https://github.com/intuit/karate/issues/743)
* Karate can start the executable (WebDriver / Chrome, WinAppDriver, Appium Server) automatically for you
* Seamlessly mix API and UI tests within the same script
* Use the power of Karate's [`match`](https://github.com/intuit/karate#prepare-mutate-assert) assertions and [core capabilities](https://github.com/intuit/karate#features) for UI element assertions

### Chrome Java API
Karate also has a Java API to automate the Chrome browser directly, designed for common needs such as converting HTML to PDF or taking a screenshot of a page. You only need the [`karate-core`](https://search.maven.org/search?q=a:karate-core) Maven artifact. Here is an [example](../karate-demo/src/test/java/driver/screenshot/ChromePdfRunner.java):

```java
import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.chrome.Chrome;
import java.io.File;
import java.util.Collections;

public class Test {

    public static void main(String[] args) {
        Chrome chrome = Chrome.startHeadless();
        chrome.setLocation("https://github.com/login");
        byte[] bytes = chrome.pdf(Collections.EMPTY_MAP);
        FileUtils.writeToFile(new File("target/github.pdf"), bytes);
        bytes = chrome.screenshot();
        // this will attempt to capture the whole page, not just the visible part
        // bytes = chrome.screenshotFull();
        FileUtils.writeToFile(new File("target/github.png"), bytes);
        chrome.quit();
    }
    
}
```

Note that in addition to `driver.screenshot()` there is a `driver.screenshotFull()` API that will attempt to capture the whole "scrolled" page, not just the part visible in the viewport.

The parameters that you can optionally customize via the `Map` argument to the `pdf()` method are documented here: [`Page.printToPDF
`](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-printToPDF).

If Chrome is not installed in the default location, you can pass a String argument like this: `Chrome.startHeadless(executable)` or `Chrome.start(executable)`. For more control or custom options, the `start()` method takes a `Map<String, Object>` argument where the following keys (all optional) are supported:
* `executable` - (String) path to the Chrome executable or batch file that starts Chrome
* `headless` - (Boolean) if headless
* `maxPayloadSize` - (Integer) defaults to 4194304 (bytes, around 4 MB), but you can override it if you deal with very large output / binary payloads

# Syntax Guide

## Examples
### Web Browser
* [Example 1](../karate-demo/src/test/java/driver/demo/demo-01.feature) - simple example that navigates to GitHub and Google Search
* [Example 2](../karate-demo/src/test/java/driver/core/test-01.feature) - which is a single script that exercises *all* capabilities of Karate Driver, so is a handy reference
### Windows
* [Example](../karate-demo/src/test/java/driver/windows/calc.feature) - but also see the [`karate-sikulix-demo`](https://github.com/ptrthomas/karate-sikulix-demo) for an alternative approach.

## Driver Configuration

### `configure driver`

This below declares that the native (direct) Chrome integration should be used, on both Mac OS and Windows - from the default installed location.

```cucumber
* configure driver = { type: 'chrome' }
```

If you want to customize the start-up, you can use a batch-file:

```cucumber
* configure driver = { type: 'chrome', executable: 'chrome' }
```

Here a batch-file called `chrome` can be placed in the system `PATH` (and made executable) with the following contents:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" $*
```

For Windows it would be `chrome.bat` in the system `PATH` as follows:

```bat
"C:\Program Files (x86)\Google\Chrome\Application\chrome" %*
```

Another example for WebDriver, again assuming that `chromedriver` is in the `PATH`:

```cucumber
{ type: 'chromedriver', port: 9515, executable: 'chromedriver' }
```

key | description
--- | -----------
`type` | see [driver types](#driver-types)
`executable` | if present, Karate will attempt to invoke this, if not in the system `PATH`, you can use a full-path instead of just the name of the executable. batch files should also work
`start` | default `true`, Karate will attempt to start the `executable` - and if the `executable` is not defined, Karate will even try to assume the default for the OS in use
`port` | optional, and Karate would choose the "traditional" port for the given `type`
`headless` | only applies to `type: 'chrome'` for now
`showDriverLog` | default `false`, will include webdriver HTTP traffic in Karate report, useful for troubleshooting or bug reports
`showProcessLog` | default `false`, will include even executable (webdriver or browser) logs in the Karate report
`addOptions` | default `null`, has to be a list / JSON array that will be appended as additional CLI arguments to the `executable`, e.g. `['--no-sandbox', '--windows-size=1920,1080']`

For more advanced options such as for Docker, CI, headless, cloud-environments or custom needs, see [`configure driverTarget`](#configure-drivertarget).

### `configure driverTarget`
The above options are fine for testing on "localhost" and when not in `headless` mode. But when the time comes for running your web-UI automation tests on a continuous integration server, things get interesting. To support all the various options such as Docker, headless Chrome, cloud-providers etc., Karate introduces the concept of a pluggable "target" where you just have to implement three methods:

```java
public interface Target {        
    
    Map<String, Object> start();
    
    Map<String, Object> stop();

    void setLogger(com.intuit.karate.Logger logger)
    
}
```

* `start()`: The `Map` returned will be used as the generated [driver configuration](#driver-configuration). And the `start()` method will be invoked as soon as a `Scenario` requests for a web-browser instance via the [`driver`](#driver) keyword.

* `stop()`: Karate will call this method at the end of a top-level `Scenario` (that has not been `call`-ed by another `Scenario`).

* `setLogger()`: You can choose to ignore this method, but if you use the provided `Logger` instance in your `Target` code, any logging you perform will nicely appear in-line with test-steps in the HTML report, which is great for troubleshooting or debugging tests.

Combined with Docker, headless Chrome and Karate's parallel-execution capability - this simple `start()` and `stop()` lifecycle can effectively run web UI automation tests in parallel on a single node.

Karate has a built-in implementation for Docker ([`DockerTarget`](src/main/java/com/intuit/karate/driver/DockerTarget.java)) that supports 2 existing Docker images out of the box:

* [`justinribeiro/chrome-headless`](https://hub.docker.com/r/justinribeiro/chrome-headless/) - for Chrome "native" in headless mode
* [`ptrthomas/karate-chrome`](#karate-chrome) - for Chrome "native" but with an option to connect to the container and view via VNC, and with video-recording

To use either of the above, you do this in a Karate test:

```cucumber
* configure driverTarget = { docker: 'justinribeiro/chrome-headless', showDriverLog: true }
```

Or for more flexibility, you could do this in [`karate-config.js`](https://github.com/intuit/karate#configuration) and perform conditional logic based on [`karate.env`](https://github.com/intuit/karate#switching-the-environment). One very convenient aspect of `configure driverTarget` is that if invoked, it will over-ride any `configure driver` directives that exist. This means that you can have the below snippet activate *only* for your CI build, and you can leave your feature files set to point to what you would use in "dev-local" mode.

```javascript
function fn() {
    var config = {
        baseUrl: 'https://qa.mycompany.com'
    };
    if (karate.env == 'ci') {
        karate.configure('driverTarget', { docker: 'ptrthomas/karate-chrome' });
    }
    return config;
}
```

To use the [recommended `--security-opt seccomp=chrome.json` Docker option](https://hub.docker.com/r/justinribeiro/chrome-headless/), add a `seccomp` property to the `driverTarget` configuration.

```javascript
karate.configure('driverTarget', { docker: 'ptrthomas/karate-chrome', seccomp: 'src/test/java/chrome.json' });
```

If you have a custom implementation of a `Target`, you can easily [construct any custom Java class](https://github.com/intuit/karate#calling-java) and pass it to `configure driverTarget`. Here below is the equivalent of the above, done the "hard way":

```javascript
var DockerTarget = Java.type('com.intuit.karate.driver.DockerTarget');
var options = { showDriverLog: true };
var target = new DockerTarget(options);
target.command = function(port){ return 'docker run -d -p ' 
    + port + ':9222 --security-opt seccomp=./chrome.json justinribeiro/chrome-headless' };
karate.configure('driverTarget', target);
```

The built-in [`DockerTarget`](src/main/java/com/intuit/karate/driver/DockerTarget.java) is a good example of how to:
* perform any pre-test set-up actions
* provision a free port and use it to shape the `start()` command dynamically
* execute the command to start the target process
* perform an HTTP health check to wait until we are ready to receive connections
* and when `stop()` is called, indicate if a video recording is present (after retrieving it from the stopped container)

Controlling this flow from Java can take a lot of complexity out your build pipeline and keep things cross-platform. And you don't need to line-up an assortment of shell-scripts to do all these things. You can potentially include the steps of deploying (and un-deploying) the application-under-test using this approach - but probably the top-level [JUnit test-suite](https://github.com/intuit/karate#parallel-execution) would be the right place for those.

#### `karate-chrome`
The [`karate-chrome`](https://hub.docker.com/r/ptrthomas/karate-chrome) Docker image adds the following capabilities to [`justinribeiro/chrome-headless`](https://hub.docker.com/r/justinribeiro/chrome-headless/):

* Chrome in "full" mode (non-headless)
* [Chrome DevTools protocol](https://chromedevtools.github.io/devtools-protocol/) exposed on port 9222
* VNC server exposed on port 5900 so that you can watch the browser in real-time
* a video of the entire test is saved to `/tmp/karate.mp4`
* after the test, when `stop()` is called, the [`DockerTarget`](src/main/java/com/intuit/karate/driver/DockerTarget.java) will embed the video into the HTML report (expand the last step in the `Scenario` to view)

To try this or especially when you need to investigate why a test is not behaving properly when running within Docker, these are the steps:

* start the container:
  * `docker run -d -p 9222:9222 -p 5900:5900 --cap-add=SYS_ADMIN ptrthomas/karate-chrome`
  * it is recommended to use [`--security-opt seccomp=chrome.json`](https://hub.docker.com/r/justinribeiro/chrome-headless/) instead of `--cap-add=SYS_ADMIN`
* point your VNC client to `localhost:5900`
  * for example on a Mac you can use this command: `open vnc://localhost:5900`
* run a test using the following [`driver` configuration](#configure-driver):
  * `* configure driver = { type: 'chrome', start: 'false', showDriverLog: true }`
* you can even use the [Karate UI](https://github.com/intuit/karate/wiki/Karate-UI) to step-through a test
* after stopping the container, you can dump the logs and video recording using this command:
  * `docker cp <container-id>:/tmp .`
  * this would include the `stderr` and `stdout` logs from Chrome, which can be helpful for troubleshooting

## Driver Types
type | default<br/>port | default<br/>executable | description
---- | ---------------- | ---------------------- | -----------
[`chrome`](https://chromedevtools.github.io/devtools-protocol/) | 9222 | mac: `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`<br/>win: `C:/Program Files (x86)/Google/Chrome/Application/chrome.exe` | "native" Chrome automation via the [DevTools protocol](https://chromedevtools.github.io/devtools-protocol/)
[`chromedriver`](https://sites.google.com/a/chromium.org/chromedriver/home) | 9515 | `chromedriver` | W3C Chrome Driver
[`geckodriver`](https://github.com/mozilla/geckodriver) | 4444 | `geckodriver` | W3C Gecko Driver (Firefox)
[`safaridriver`](https://webkit.org/blog/6900/webdriver-support-in-safari-10/) | 5555 | `safaridriver` | W3C Safari Driver
[`mswebdriver`](https://docs.microsoft.com/en-us/microsoft-edge/webdriver) | 17556 | `MicrosoftWebDriver` | W3C Microsoft Edge WebDriver
[`msedge`](https://docs.microsoft.com/en-us/microsoft-edge/devtools-protocol/) | 9222 | `MicrosoftEdge` | *very* experimental - using the DevTools protocol
[`winappdriver`](https://github.com/Microsoft/WinAppDriver) | 4727 | `C:/Program Files (x86)/Windows Application Driver/WinAppDriver` | Windows Desktop automation, similar to Appium
[`android`](https://github.com/appium/appium/) | 4723 | `appium` | android automation via [Appium](https://github.com/appium/appium/)
[`ios`](https://github.com/appium/appium/) | 4723 |`appium` | iOS automation via [Appium](https://github.com/appium/appium/)

## Locators
The standard locator syntax is supported. For example for web-automation, a `/` prefix means XPath and else it would be evaluated as a "CSS selector".

```cucumber
And driver.input('input[name=someName]', 'test input')
When driver.submit("//input[@name='commit']")
```

platform | prefix | means | example
----- | ------ | ----- | -------
web | (none) | css selector | `input[name=someName]`
web <br/> android <br/> ios | `/` | xpath | `//input[@name='commit']`
web | `^` | link text | `^Click Me`
web | `*` | partial link text | `*Click Me`
win <br/> android <br/> ios| (none) | name | `Submit`
win <br/> android <br/> ios | `@` | accessibility id | `@CalculatorResults`
win <br/> android <br/> ios | `#` | id | `#MyButton`
ios| `:` | -ios predicate string | `:name == 'OK' type == XCUIElementTypeButton`
ios| `^` | -ios class chain | `^**/XCUIElementTypeTable[name == 'dataTable']`
android| `-` | -android uiautomator | `-input[name=someName]`

# Keywords
Only one keyword sets up UI automation in Karate, typically by specifying the URL to open in a browser. And then you would use the built-in [`driver`](#js-api) JS object for all other operations, combined with Karate's [`match`](https://github.com/intuit/karate#prepare-mutate-assert) syntax for assertions where needed.

## `driver`
Navigate to a web-address and initializes the `driver` instance for future step operations as per what is [configured](#configure-driver). And yes, you can use [variable expressions](https://github.com/intuit/karate#karate-expressions) from [`karate-config.js`](https://github.com/intuit/karate#configuration). For example:

```cucumber
Given driver webUrlBase + '/page-01'
```
### `driver` JSON
A variation where the argument is JSON instead of a URL / address-string, used only if you are testing a desktop (or mobile) application, and for Windows, you can provide the `app`, `appArguments` and other parameters expected by the [WinAppDriver](https://github.com/Microsoft/WinAppDriver). For example:

```cucumber
Given driver { app: 'Microsoft.WindowsCalculator_8wekyb3d8bbwe!App' }
```

# JS API
The built-in `driver` JS object is where you script UI automation. It will be initialized only after the [`driver`](#driver) keyword has been used to navigate to a web-page (or application).

Behind the scenes this does an [`eval`](https://github.com/intuit/karate#eval) - and you can omit the `eval` keyword when calling a method (or setting a property) on it.

You can refer to the [Java interface definition](src/main/java/com/intuit/karate/driver/Driver.java) of the `driver` object to better understand what the various operations are. Note that `Map<String, Object>` [translates to JSON](https://github.com/intuit/karate#type-conversion), and JavaBean getters and setters translate to JS properties - e.g. `driver.getTitle()` becomes `driver.title`.

## Short Cuts
Asa convenience, *all* the methods on the `driver` have been injected into the context as special (JavaScript) variables so you can omit the "`driver.`" part and save a lot of typing. For example instead of:

```cucumber
And driver.input('#eg02InputId', Key.SHIFT)
Then match driver.text('#eg02DivId') == '16'
```

You can shorten all that to:

```cucumber
And input('#eg02InputId', Key.SHIFT)
Then match text('#eg02DivId') == '16'
```

When it comes to JavaBean getters and setters, you could call them directly, but the `driver.propertyName` form is much better to read, and you save the trouble of typing out round brackets. So instead of doing this:

```cucumber
And match getLocation() contains 'page-01'
When setLocation(webUrlBase + '/page-02')
```

You should prefer this form, which is more readable:
```cucumber
And match driver.location contains 'page-01'
When driver.location = webUrlBase + '/page-02'
```
### Chaining
All the methods that return a [`Driver`](src/main/java/com/intuit/karate/driver/Driver.java) or [`Element`](src/main/java/com/intuit/karate/driver/Element.java) are "chain-able" which means you can combine them to signal certain types of "intent" for example to [`retry()`](#retry) until an HTML element is present:

```cucumber
* retry().click('#someId')
```

## `driver.location`
Get the current URL / address for matching. Example:

```cucumber
Then match driver.location == webUrlBase + '/page-02'
```

This can also be used as a "setter": 
```cucumber
* driver.location = 'http://localhost:8080/test'
```

## `driver.title`
Get the current page title for matching. Example:

```cucumber
Then match driver.title == 'Test Page'
```

## `driver.dimensions`
Set the size of the browser window:
```cucumber
 And driver.dimensions = { x: 0, y: 0, width: 300, height: 800 }
```

This also works as a "getter" to get the current window dimensions.
```cucumber
* def dims = driver.dimensions
```
The result JSON will be in the form: `{ x: '#number', y: '#number', width: '#number', height: '#number' }`

## `position()`
Get the position and size of an element by [locator](#locators) as follows:
```cucumber
* def pos = position('#someid')
```
The result JSON will be in the form: `{ x: '#number', y: '#number', width: '#number', height: '#number' }`

## `input()`
2 string arguments: [locator](#locators) and value to enter.
```cucumber
* driver.input('input[name=someName]', 'test input')
```

Special keys such as `ENTER`, `TAB` etc. can be specified like this:

```cucumber
* input('#someInput', 'test input' + Key.ENTER)
```

A special variable called `Key` will be available and you can see all the possible key codes [here](src/main/java/com/intuit/karate/driver/Key.java).

Also see [`value(locator, value)`](#valueset) and [`driver.clear()`](#clear)

## `submit()`
Karate has an [elegant approach](#chaining) to handling any action such as [`click()`](#click) that results in a new page load. You "signal" that a submit is expected by calling the `submit()` function (which returns a `Driver` object) and then "chaining" the action that is expected to trigger a page load.

```cucumber
When submit().click('*Page Three')
```

The advantage of this approach is that it works with any of the actions. So even if your next step is the `ENTER` key, you can do this:

```cucumber
When submit().input('#someform', Key.ENTER)
```

Karate will do the best it can to detect a page change and wait for the load to complete before proceeding to *any* step that follows.

## `delay()`
Of course, you should *never* use this in a UI test (use [`wait()`](#wait) instead) but sometimes it is needed, for example to wait for animations to render before taking a screenshot. The nice thing here is that it returns a [`Driver`](#chaining) instance, so you can chain any other method and the "intent" will be clear. For example:

```cucumber
* delay(1000).screenshot()
```

## `click()`
Just triggers a click event on the DOM element
```cucumber
* driver.click('input[name=someName]')
```

Also see [`submit()`](#submit)

## `select()`
Note that most of the time, it is better to fire a [`click()`](#click) on the `<option>` element. But you can try this for normal `<select>` boxes that have not been overly "ehnanced" by JavaScript. There are four variations and use the [locator](#locators) conventions.

```cucumber
# select by displayed text
Given select('select[name=data1]', '^Option Two')

# select by partial displayed text
And select('select[name=data1]', '*Two')

# select by `value`
Given select('select[name=data1]', 'option2')

# select by index
Given select('select[name=data1]', 2)
```

## `focus()`
```cucumber
* focus('.myClass')
```

## `clear()`
```cucumber
* clear('#myInput')
```
If this does not work, try [`value(selector, value)`](#valueset)

## `scroll()`
Scrolls to the element.
```cucumber
* scroll('#myInput')
```

Since a `scroll()` + `click()` or `input()` is a common combination, you can [chain](#chaining) these:

```cucumber
* scroll('#myBtn').click()
* scroll('#myTxt').input('hello')
```

## `close()`
Close the page / tab.

## `quit()`
Close the browser.

## `html()`
Get the `outerHTML`, so will include the markup of the selected element. Useful for `match contains` assertions. Example:

```cucumber
And match html('#eg01DivId') == '<div id="eg01DivId">this div is outside the iframe</div>'
```

## `text()`
Get the text-content. Example:
```cucumber
And match text('.myClass') == 'Class Locator Test'
```

## `value()`
Get the HTML form-element value. Example:
```cucumber
And match value('.myClass') == 'some value'
```

## `value(set)`
Set the HTML form-element value. Example:
```cucumber
When value('#eg01InputId', 'something more')
```

## `attribute()`
Get the HTML element attribute value. Example:
```cucumber
And match attribute('#eg01SubmitId', 'type') == 'submit'
```

## `enabled()`
If the element is `enabled` and not `disabled`:
```cucumber
And match enabled('#eg01DisabledId') == false
```

Also see [`wait()`](#wait) for an example of how to *wait* until an element is enabled or until any other element property becomes the target value.

## `waitUntil()`
Wait for the JS expression to evaluate to `true`. Will poll using the retry settings [configured](https://github.com/intuit/karate#retry-until).
```cucumber
* waitUntil("document.readyState == 'complete'")
```

## `waitForPage()`
Short-cut for the commonly used `waitUntil("document.readyState == 'complete'")`

## `waitFor()`
Will wait until the element (by [locator](#locators)) is present in the page and uses the re-try settings for [`waitUntil()`](#waituntil). This will fail the test if the element does not appear after the configured number of re-tries.

```cucumber
And waitFor('#eg01WaitId')
```

Instead of using `waitFor()` you would typically ["chain"](#chaining) a [`retry()`](#retry) like this:

```cucumber
And retry().click('#eg01WaitId')
```

## `waitUntil()`
A very useful variant of [`waitFor()`](#waitfor) is where you supply a JavaScript "predicate" function (or expression) that will be evaluated *on* the element returned  by the selector in the HTML DOM. Note that most of the time you will prefer the short-cut form that begins with an underscore (or bang), and Karate will inject the JavaScript DOM element reference into the variable named "`_`".

This is especially useful for waiting for some HTML element to stop being `disabled`. Note that Karate will fail the test if the `waitUntil()` failed even after the configured number of [re-tries](#retry).

> One limitation is that you cannot use double-quotes in these expressions, so stick to the pattern below.

```cucumber
And waitUntil('#eg01WaitId', "function(e){ return e.innerHTML == 'APPEARED!' }")

# if the expression begins with "_" or "!", Karate will wrap the function for you !
And assert waitUntil('#eg01WaitId', "_.innerHTML == 'APPEARED!'")
And assert waitUntil('#eg01WaitId', '!_.disabled')
```

## `retry()`
For tests that need to wait for slow pages or un-predictable loading times for elements, Karate allows you to *temporarily* tweak the internal retry settings. Here are the few things you need to know.

* the [default retry settings](https://github.com/intuit/karate#retry-until) are
  * `count`: 3, `interval`: 3000 milliseconds
  * it is recommended you stick to these, which should suffice for most applications
  * if you really want, you can change this "globally" like this:
    * `configure('retry', { count: 10, interval: 5000 });` in [`karate-config.js`](https://github.com/intuit/karate#configuration)
    * or *any time* within a script like this: `* configure retry = { count: 10, interval: 5000 }`
* by default any actions such as `click()` will *not* be re-tried and this is what you need most of the time for smooth tests
  * but some troublesome parts of your flow will require re-tries and this is where the `retry()` API comes in
  * there are 3 forms:
    * `retry()` - just signals that the *next* action will be re-tried if it fails, using the [currently configured retry settings](https://github.com/intuit/karate#retry-until)
    * `retry(count)` - the next action will *temporarily* use the `count` provided as the limit for retry-attempts
    * `retry(count, interval)` - *temporarily* change the retry `count` *and* retry `interval` (in milliseconds) for the next action

And since you can [`chain()`](#chaining) the `retry()` API, you can have tests that clearly express the "intent to wait", and as one-liners, only where you need it:

```cucumber
* retry().click('#someButton')
* retry(5).input('#someTxt', 'hello')
* retry(3, 10000).waitUntil('#reallySlowButton', '!_.disabled')
```

## `script()`
Will actually attempt to evaluate the given string as JavaScript within the browser.

```cucumber
* assert 3 == script("1 + 2")
```

A more useful variation is to perform a JavaScript `eval` on a reference to the HTML DOM element retrieved by a [locator](#locators). For example:

```cucumber
And match script('#eg01WaitId', "function(e){ return e.innerHTML }") == 'APPEARED!'
# which can be shortened to:
And match script('#eg01WaitId', '_.innerHTML') == 'APPEARED!'
```

Normally you would use [`text()`](#text) to do the above, but you get the idea. Expressions follow the same short-cut rules as for [`wait()`](#wait).

## `scripts()`
Just like the above against an element by [locator](#locators), but will perform the script `eval()` on *all* matching elements (not just the first) and return the results as a JSON array / list.

```cucumber
# get text for all elements that match css selector
When def list = scripts('div div', '_.textContent')
Then match list == '#[3]'
And match each list contains '@@data'
```

## `refresh()`
Normal page reload, does not clear cache.

## `reload()`
Hard page reload, after clearing the cache.

## `back()`

## `forward()`

## `maximize()`

## `minimize()`

## `fullscreen()`

## `driver.cookie` 
Set a cookie:
```cucumber
Given def cookie2 = { name: 'hello', value: 'world' }
When driver.cookie = cookie2
Then match driver.cookies contains '#(^cookie2)'
```

## `cookie()`
Get a cookie by name:
```cucumber
* def cookie1 = { name: 'foo', value: 'bar' }
And match driver.cookies contains '#(^cookie1)'
And match cookie('foo') contains cookie1
```

## `driver.cookies`
See above examples.

## `deleteCookie()`
Delete a cookie by name:
```cucumber
When deleteCookie('foo')
Then match driver.cookies !contains '#(^cookie1)'
```

## `clearCookies()`
Clear all cookies.
```cucumber
When clearCookies()
Then match driver.cookies == '#[0]'
```

## `dialog()`
Two forms. The first takes a single boolean argument, whether to "accept" or "cancel". The second form has an additional string argument which is the text to enter for cases where the dialog is expecting user input.

Also works as a "getter" to retrieve the text of the currently visible dialog:
```cucumber
* match driver.dialog == 'Please enter your name:'
```

## `switchPage()`
When multiple browser tabs are present, allows you to switch to one based on page title (or URL).

```cucumber
When switchPage('Page Two')
```

## `switchFrame()`
This "sets context" to a chosen frame (or `<iframe>`) within the page. There are 2 variants, one that takes an integer as the param, in which case the frame is selected based on the order of appearance in the page:

```cucumber
When switchFrame(0)
```

Or you use a [locator](#locators) that points to the `<iframe>` element that you need to "switch to".

```cucumber
When switchFrame('#frame01')
```

After you have switched, any future actions such as [`click()`](#click) would operate within the "selected" `<iframe>`. To "reset" so that you are back to the "root" page, just switch to `null` (or integer value `-1`):

```cucumber
When switchFrame(null)
```

## `screenshot()`
Two forms, if a [locator](#locators) is provided - only that HTML element will be captured, else the entire browser viewport will be captured. A byte array will be returned.

This will also do automatically perform a [`karate.embed()`](https://github.com/intuit/karate#karate-embed) so that the image appears in the HTML report.

```cucumber
* screenshot()
# or
* screenshot('#someDiv')
```

If you want to disable the HTML report "auto-embedding", pass an additional boolean argument as `false`, e.g: 

```cucumber
* screenshot(false)
# or
* screenshot('#someDiv', false)
```

## `screenshotFull()`
Only supported for driver type [`chrome`](#driver-types). See [Chrome Java API](#chrome-java-api). This will snapshot the entire page, not just what is visible in the viewport.

## `pdf()`
Only supported for driver type [`chrome`](#driver-types). See [Chrome Java API](#chrome-java-api).

## `highlight()`
To visually highlight an element in the browser, especially useful when working in the [Karate UI](https://github.com/intuit/karate/wiki/Karate-UI)

```cucumber
* highlight('#eg01DivId')
```

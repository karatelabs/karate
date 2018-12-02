# Karate Driver
## UI Test Automation Made Simple

## Introduction
> This is new, and this first version 0.9.0 should be considered experimental.

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
* The Windows example above proves that the approach would work for Appium with minimal changes (please contribute !)
* Karate can start the executable (WebDriver / Chrome, WinAppDriver, Appium Server) automatically for you
* Seamlessly mix API and UI tests within the same script
* Use the power of Karate's [`match`](https://github.com/intuit/karate#prepare-mutate-assert) assertions and [core capabilities](https://github.com/intuit/karate#features) for UI element assertions

### Chrome Java API
Karate also has a Java API to automate the Chrome browser directly, designed for common needs such as converting HTML to PDF or taking a screenshot of a page. Here is an [example](../karate-demo/src/test/java/driver/screenshot/ChromePdfRunner.java):

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
        FileUtils.writeToFile(new File("target/github.png"), bytes);
        chrome.quit();
    }
    
}
```

The parameters that you can optionally customize via the `Map` argument to the `pdf()` method are documented here: [`Page.printToPDF
`](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-printToPDF).

If Chrome is not installed in the default location, you can pass a String argument like this: `Chrome.startHeadless(executable)` or `Chrome.start(executable)`.

# Syntax Guide

## Examples
### Web Browser
* [Example 1](../karate-demo/src/test/java/driver/demo/demo-01.feature)
* [Example 2](../karate-demo/src/test/java/driver/core/test-01.feature)
### Windows
* [Example](../karate-demo/src/test/java/driver/windows/calc.feature)

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

## Driver Types
type | description
---- | -----------
[`chrome`](https://chromedevtools.github.io/devtools-protocol/) | "native" Chrome automation via the [DevTools protocol](https://chromedevtools.github.io/devtools-protocol/)
[`chromedriver`](https://sites.google.com/a/chromium.org/chromedriver/home) | W3C Chrome Driver
[`geckodriver`](https://github.com/mozilla/geckodriver) | W3C Gecko Driver (Firefox)
[`safaridriver`](https://webkit.org/blog/6900/webdriver-support-in-safari-10/) | W3C Safari Driver
[`mswebdriver`](https://docs.microsoft.com/en-us/microsoft-edge/webdriver) | W3C Microsoft Edge WebDriver
[`msedge`](https://docs.microsoft.com/en-us/microsoft-edge/devtools-protocol/) | *very* experimental - using the DevTools protocol
[`winappdriver`](https://github.com/Microsoft/WinAppDriver) | Windows Desktop automation, similar to Appium

## Locators
The standard locator syntax is supported. For example for web-automation, a `/` prefix means XPath and else it would be evaluated as a "CSS selector".

```cucumber
And driver.input('input[name=someName]', 'test input')
When driver.submit("//input[@name='commit']")
```

web ? | prefix | means | example
----- | ------ | ----- | -------
web | (none) | css selector | `input[name=someName]`
web | `/` | xpath | `//input[@name='commit']`
web | `^` | link text | `^Click Me`
web | `*` | partial link text | `*Click Me`
win | (none) | name | `Submit`
win | `@` | accessibility id | `@CalculatorResults`
win | `#` | id | `#MyButton`

## Keywords
Only one keyword sets up UI automation in Karate, typically by specifying the URL to open in a browser. And then you would use the built-in [`driver`](#js-api) JS object for all other operations, combined with Karate's [`match`](https://github.com/intuit/karate#prepare-mutate-assert) syntax for assertions where needed.

### `driver`
Navigate to a web-address and initializes the `driver` instance for future step operations as per what is [configured](#configure-driver). And yes, you can use [variable expressions](https://github.com/intuit/karate#karate-expressions) from [`karate-config.js`](https://github.com/intuit/karate#configuration). For example:

```cucumber
Given driver webUrlBase + '/page-01'
```
#### `driver` JSON
A variation where the argument is JSON instead of a URL / address-string, used only if you are testing a desktop (or mobile) application, and for Windows, you can provide the `app`, `appArguments` and other parameters expected by the [WinAppDriver](https://github.com/Microsoft/WinAppDriver). For example:

```cucumber
Given driver { app: 'Microsoft.WindowsCalculator_8wekyb3d8bbwe!App' }
```

## JS API
The built-in `driver` JS object is where you script UI automation.

Behind the scenes this does an [`eval`](https://github.com/intuit/karate#eval) - and as a convenience, you can omit the `eval` keyword when executing an action - and when you don't need to save any result using [`def`](https://github.com/intuit/karate#def).

You can refer to the [Java interface definition](src/main/java/com/intuit/karate/driver/Driver.java) of the `driver` object to better understand what the various operations are. Note that `Map<String, Object>` [translates to JSON](https://github.com/intuit/karate#type-conversion), and JavaBean getters and setters translate to JS properties - e.g. `driver.getTitle()` becomes `driver.title`.

### `driver.location`
Get the current URL / address for matching. Example:

```cucumber
Then match driver.location == webUrlBase + '/page-02'
```

This can also be used as a "setter": 
```cucumber
* driver.location = 'http://localhost:8080/test'
```

### `driver.title`
Get the current page title for matching. Example:

```cucumber
Then match driver.title == 'Test Page'
```

### `driver.dimensions`
```cucumber
 And driver.dimensions = { left: 0, top: 0, width: 300, height: 800 }
 ```

### `driver.input()`
2 string arguments: [locator](#locators) and value to enter.
```cucumber
* driver.input('input[name=someName]', 'test input')
```

### `driver.click()`
Just triggers a click event on the DOM element, does *not* wait for a page load.
```cucumber
* driver.click('input[name=someName]')
```

There is a second rarely used variant which will wait for a JavaScript [dialog](#driverdialog) to appear:
```cucumber
* driver.click('input[name=someName]', true)
```

### `driver.submit()`
Triggers a click event on the DOM element, *and* waits for the next page to load.
```cucumber
* driver.submit('.myClass')
```

### `driver.select()`
Specially for select boxes. There are four variations and use the [locator](#locators) conventions.

```cucumber
# select by displayed text
Given driver.select('select[name=data1]', '^Option Two')

# select by partial displayed text
And driver.select('select[name=data1]', '*Two')

# select by `value`
Given driver.select('select[name=data1]', 'option2')

# select by index
Given driver.select('select[name=data1]', 2)
```

### `driver.focus()`
```cucumber
* driver.focus('.myClass')
```

### `driver.close()`
Close the page / tab.

### `driver.quit()`
Close the browser.

### `driver.html()`
Get the `innerHTML`. Example:
```cucumber
And match driver.html('.myClass') == '<span>Class Locator Test</span>'
```

### `driver.text()`
Get the text-content. Example:
```cucumber
And match driver.text('.myClass') == 'Class Locator Test'
```

### `driver.value()`
Get the HTML form-element value. Example:
```cucumber
And match driver.value('.myClass') == 'some value'
```

### `driver.attribute()`
Get the HTML element attribute value. Example:
```cucumber
And match driver.attribute('#eg01SubmitId', 'type') == 'submit'
```

### `driver.waitUntil()`
Wait for the JS expression to evaluate to `true`. Will poll using the retry settings [configured](https://github.com/intuit/karate#retry-until).
```cucumber
* eval driver.waitUntil("document.readyState == 'complete'")
```

### `driver.eval()`
Will actually attempt to evaluate the given string as JavaScript within the browser.

### `driver.refresh()`
Normal page reload, does not clear cache.

### `driver.reload()`
Hard page reload, after clearing the cache.

### `driver.back()`

### `driver.forward()`

### `driver.maximize()`

### `driver.minimize()`

### `driver.fullscreen()`

### `driver.cookie` 
Set a cookie:
```cucumber
Given def cookie2 = { name: 'hello', value: 'world' }
When driver.cookie = cookie2
Then match driver.cookies contains '#(^cookie2)'
```

### `driver.cookie()`
Get a cookie by name:
```cucumber
* def cookie1 = { name: 'foo', value: 'bar' }
And match driver.cookies contains '#(^cookie1)'
And match driver.cookie('foo') contains cookie1
```

### `driver.cookies`
See above examples.

### `driver.deleteCookie()`
Delete a cookie by name:
```cucumber
When driver.deleteCookie('foo')
Then match driver.cookies !contains '#(^cookie1)'
```

### `driver.clearCookies()`
Clear all cookies.
```cucumber
When driver.clearCookies()
Then match driver.cookies == '#[0]'
```

### `driver.dialog()`
Two forms. The first takes a single boolean argument, whether to "accept" or "cancel". The second form has an additional string argument which is the text to enter for cases where the dialog is expecting user input.

Also works as a "getter" to retrieve the text of the currently visible dialog:
```cucumber
* match driver.dialog == 'Please enter your name:'
```

### `driver.screenshot()`
Two forms, if a [locator](#locators) is provided only that HTML element will be captured, else the browser viewport will be captured.

### `driver.pdf()`
Only supported for driver type [`chrome`](#driver-types). See [Chrome Java API](#chrome-java-api).

### `driver.highlight()`
Useful to visually highlight an element in the browser, especially when working in the [Karate UI](https://github.com/intuit/karate/wiki/Karate-UI)

```cucumber
* driver.highlight('#eg01DivId')
```

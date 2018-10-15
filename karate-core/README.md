# Karate Driver
## UI Test Automation Made Simple

## Introduction
> This is new, and this first version 0.9.0 should be considered experimental.

Especially after [the Gherkin parser and execution engine were re-written from the ground-up](https://github.com/intuit/karate/issues/444#issuecomment-406877530), Karate is arguably a mature framework that elegantly solves quite a few test-automation engineering challenges, such as [parallel execution](https://twitter.com/KarateDSL/status/1049321708241317888), [data-driven testing](https://github.com/intuit/karate#data-driven-tests), [environment-switching](https://github.com/intuit/karate#switching-the-environment) and [easy-yet-powerful assertions](https://github.com/intuit/karate#contains-short-cuts).

Which led us to think, what if we could add UI automation without disturbing the core HTTP API testing capabilities. So we gave it a go, and we are releasing the results so far as this experimental version.

Please do note: this is work in progress and all actions needed for test-automation may not be in-place. But we hope that releasing this sooner would result in more users trying this in a variety of environments. And that they provide valuable feedback and even contribute code where possible.

We know too well that UI automation is hard to get right and suffers from 2 big challenges, what we like to call the "*flaky test*" problem and the "*wait for UI element*" problem.

With the help of the community, we would like to try valiantly - to see if we can get close to as ideal a state a possible. So wish us luck !

Some highlights:

* Direct-to-Chrome automation using the [DevTools protocol](https://chromedevtools.github.io/devtools-protocol/)
* [W3C WebDriver](https://w3c.github.io/webdriver/) support
* [Cross-Browser support](https://twitter.com/ptrthomas/status/1048260573513666560) including [Microsoft Edge on Windows](https://twitter.com/ptrthomas/status/1046459965668388866) and [Safari on Mac](https://twitter.com/ptrthomas/status/1047152170468954112)
* WebDriver support without any intermediate server

# Example
For now refer to these two examples along with the syntax guide:
* [Example 1](../karate-junit4/src/test/java/com/intuit/karate/junit4/http/chrome.feature)
* [Example 2](../karate-demo/src/test/java/web/core/test-01.feature)

# Syntax Guide
## `configure driver`

Example (Direct-to-Chrome):

```cucumber
* configure driver = { type: 'chrome', executable: 'chrome' }
```

In the above example, a batch-file called `chrome` was created in the system `PATH` with the following contents:

```sh
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" $*
```

> TODO Windows example for the above.

Another example for WebDriver, again assuming that `chromedriver` is in the `PATH`:

```cucumber
{ type: 'chromedriver', port: 9515, executable: 'chromedriver' }
```

key | description
--- | -----------
`type` | `chrome` - "native" Chrome automation via the [DevTools protocol](https://chromedevtools.github.io/devtools-protocol/) <br/>[`chromedriver`](https://sites.google.com/a/chromium.org/chromedriver/home) <br/>[`geckodriver`](https://github.com/mozilla/geckodriver) <br/>[`safaridriver`](https://webkit.org/blog/6900/webdriver-support-in-safari-10/) <br/>[`mswebdriver`](https://docs.microsoft.com/en-us/microsoft-edge/webdriver) <br/>[`msedge`](https://docs.microsoft.com/en-us/microsoft-edge/devtools-protocol/) (*Very* Experimental)
`executable` | if present, Karate will attempt to invoke this, if not in the system `PATH`, you can use a full-path instead of just the name of the executable. batch files should also work
`port` | optional, and Karate would choose the "traditional" port for the given `type`
`headless` | (not ready yet, nearly done for `chrome`, but needs some testing)

## `location`
Navigate to a web-address. And yes, you can use [variable expressions](https://github.com/intuit/karate#karate-expressions) from [config](https://github.com/intuit/karate#configuration). Example:

```cucumber
Given location webUrlBase + '/page-01'
```

## `input`
```cucumber
And input #eg01InputId = 'hello world'
```

The standard locator syntax is supported, a `#` prefix means by `id` a `/` prefix means XPath and else it would be evaluated as a "CSS selector" for example:

```cucumber
And input input[name=someName] = 'test input'
```

## 

# JS API
In some cases, especially when using dynamic data in scope as Karate variables, the built-in `driver` object can be used instead of the DSL keywords listed above.

## `karate.location`
This is the only one that is on the `karate` object, the rest are on the `driver` object. Because this switches Karate into "driver" mode for UI testing.

```cucumber
* eval karate.location = 'https://google.com'
```

## `driver.location`
Get the current URL / address for matching. Example:

```cucumber
Then match driver.location == webUrlBase + '/page-02'
```

## `driver.title`
Get the current page title for matching. Example:

```cucumber
Then match driver.title == 'Test Page'
```

## `driver.dimensions`
```cucumber
 And eval driver.dimensions = { left: 0, top: 0, width: 300, height: 800 }
 ```

## `driver.input()`
```cucumber
* eval driver.input('input[name=someName]', 'test input')
```

## `driver.click()`
```cucumber
* eval driver.click('input[name=someName]')
```

## `driver.submit()`
The difference from `click` is that it would wait for a new page to load.
```cucumber
* eval driver.submit('.myClass')
```

## `driver.focus()`
```cucumber
* eval driver.focus('.myClass')
```

## `driver.close()`
Close the page.

## `driver.quit()`
Close the browser.

## `driver.html()`
Get the `innerHTML`. Example:
```cucumber
And match driver.html('.myClass') == '<span>Class Locator Test</span>'
```

## `driver.text()`
Get the text-content. Example:
```cucumber
And match driver.text('.myClass') == 'Class Locator Test'
```

## `driver.value()`
Get the HTML form-element value. Example:
```cucumber
And match driver.value('.myClass') == 'some value'
```

## `driver.waitForEvalTrue()`
Wait for the JS expression to evaluate to `true`.

## `driver.refresh()`

## `driver.reload()`
Including cache

## `driver.back()`

## `driver.forward()`

## `driver.maximize()`

## `driver.minimize()`

## `driver.fullscreen()`


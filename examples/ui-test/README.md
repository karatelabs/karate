# Karate UI Test
This project is designed to be the simplest way to replicate issues with the [Karate UI framework](https://github.com/intuit/karate/tree/master/karate-core) for web-browser testing. It includes an HTTP mock that serves HTML and JavaScript, which you can easily modify to simulate complex situations such as a slow-loading element. To submit an issue after you have a way to replicate the scenario, follow these instructions: [How to Submit an Issue](https://github.com/intuit/karate/wiki/How-to-Submit-an-Issue).

## Overview
To point to a specifc version of Karate, edit the `pom.xml`. If you are working with the source-code of Karate, follow the [developer guide](https://github.com/intuit/karate/wiki/Developer-Guide).

You can double-click and view `page-01.html` to see how it works. It depends on `karate.js` which is very simple, so you can see how to add any JS (if required) along the same lines.

The `mock.feature` is a Karate mock. Note how it is very simple - but able to serve both HTML and JS. If you need to include navigation to a second page, you can easily add a second HTML file and `Scenario`. To test the HTML being served manually, you can start the mock-server by running `MockRunner` as a JUnit test, and then opening [`http://localhost:8080/page-01`](http://localhost:8080/page-01) in a browser.

## Running
The `test.feature` is a simple [Karate UI test](https://github.com/intuit/karate/tree/master/karate-core), and executing `UiRunner` as a JUnit test will run it. You will be able to open the HTML report (look towards the end of the console log) and refresh it after re-running. For convenience, this test is a `Scenario Outline` - set up so that you can add multiple browser targets or driver implementations. This makes it easy to validate cross-browser compatibility.

## Debugging
You should be able to use the [Karate extension for Visual Studio Code](https://github.com/intuit/karate/wiki/IDE-Support#vs-code-karate-plugin) for stepping-through a test for troubleshooting.

## WebDriver Tips
If you are targeting a WebDriver implementation, you may need to experiment with HTTP calls. Don't forget that that is Karate's core competency ! So you can use a "scratchpad" Karate test on the side, like this, after you have manually started a "driver executable", [`chromedriver`](https://chromedriver.chromium.org) in this case:

```cucumber
Feature:

Scenario:
* url 'http://localhost:9515'
* path 'session'
* request {"capabilities":{"browserName":"msedge"}}
* method post
```

Within a test script, as a convenience, the `driver` object exposes an `http` property, which makes it easy to make custom-crafted WebDriver requests using the [`Http` helper / class](../../karate-core/src/main/java/com/intuit/karate/Http.java). Note that this will be available only after the [`driver` keyword](https://github.com/intuit/karate/tree/master/karate-core#driver) has been used, and thus a WebDriver session has been initialized.

Here is an example of [getting the page title](https://w3c.github.io/webdriver/#get-title):

```cucumber
* def temp = driver.http.path('title').get().body().asMap()
* print 'temp:', temp
```

Which results in a `GET` request to: `http://localhost:9515/session/{sessionId}/title` - and the response body will be printed. Now you can easily extract data out of the response JSON.

And here is how you can make a `POST` request, to [navigate to a given URL](https://w3c.github.io/webdriver/#navigate-to):

```cucumber
* driver.http.path('url').post({ url: 'https://github.com' })
```

And note that the [VS Code "Karate Runner"](https://github.com/intuit/karate/wiki/IDE-Support#vs-code-karate-plugin) plugin is really convenient for re-running tests - or you can pause a test using a break-point and [type in interactive commands](https://twitter.com/KarateDSL/status/1167533484560142336).

## DevTools Protocol Tips
When using the driver type `chrome`, you can call the `send()` method and pass a raw JSON message that will be sent to the Chrome browser using a WebSocket connection. For example here is how to get the [metadata about frames](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-getFrameTree):

```cucumber
* def temp = driver.send({ method: 'Page.getFrameTree' })
* print 'temp:', temp
```

This will result in the following raw message sent (Karate will supply the `id` automatically):

```
{"method":"Page.getFrameTree","id":7}
```

Chrome will respond with something like this, which should be viewable in the log / console:

```
{"id":7,"result":{"frameTree":{"frame":{"id":"11B3A5ABDEE5802201D84389EE0215B8","loaderId":"D2241AD7B86ED533F095F907A78A1208","url":"http://localhost:52664/page-01","securityOrigin":"http://localhost:52664","mimeType":"text/html"}}}}
```

You can do more, but this should be sufficient for exploring the possible commands and troubleshooting via trial and error. And suggest / contribute changes to be made to the code, e.g. the [DevToolsDriver](../../karate-core/src/main/java/com/intuit/karate/driver/DevToolsDriver.java).
# Karate UI Test
This project is designed to be the simplest way to replicate issues with the [Karate UI framework](https://github.com/intuit/karate/tree/master/karate-core) for web-browser testing. It includes an HTTP mock that serves HTML and JavaScript, which you can easily modify to simulate complex situations such as a slow-loading element.

## Overview
To point to a specifc version of Karate, edit the `pom.xml`. If you are working with the source-code of Karate, follow the [developer guide](https://github.com/intuit/karate/wiki/Developer-Guide).

You can double-click and view `page-01.html` to see how it works. It depends on `karate.js` which is very simple, so you can see how to add any JS (if required) along the same lines.

The `mock.feature` is a Karate mock. Note how it is very simple - but able to serve both HTML and JS. If you need to include navigation to a second page, you can easily add a second HTML file and `Scenario`. To test the HTML being served manually, you can start the mock-server by running `MockRunner` as a JUnit test, and then opening [`http://localhost:8080/page-01`](http://localhost:8080/page-01) in a browser.

## Running
The `test.feature` is a simple [Karate UI test](https://github.com/intuit/karate/tree/master/karate-core), and executing `UiRunner` as a JUnit test will run it. You will be able to open the HTML report (look towards the end of the console log) and refresh it after re-running. For convenience, this test is a `Scenario Outline` - set up so that you can add multiple browser targets or driver implementations. This makes it easy to validate cross-browser compatibility.

## Debugging
You should be able to use the [Karate extension for Visual Studio Code](https://github.com/intuit/karate/wiki/IDE-Support#vs-code-karate-plugin) for stepping-through a test for troubleshooting.
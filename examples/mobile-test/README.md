# Examples - Karate Mobile (Appium)


## Overview
This project is to replicate issues with the [Karate UI framework](https://github.com/intuit/karate/tree/develop/karate-core) for mobile app testing using appium.

## Running
> Before running:  
> * change `karate.version` in [`pom.xml`](pom.xml)
> * change `platformVersion`,`avd` in [`karate-config.js`](src/test/java/karate-config.js) according to your local android `avd` setup and add/remove the desiredCapabilities according to your requirement.
> * start appium server manually when `android.feature` has `configure driver` with `start: false`
> * change `configure driver` to `start: true` if you want karate to start appium driver automatically (this is experimental and needs appium pre-installed via `npm`)


The [`android.feature`](src/test/java/android/android.feature) is a simple [Karate UI test](https://github.com/intuit/karate/tree/develop/karate-core), and executing `AndroidRunner` as a JUnit test will run it.

Refer [`Documentation`](https://github.com/intuit/karate/tree/develop/karate-core#appium) for currently supported methods

Feature: Image comparison demo

Background:
    * configure driver = { type: 'chrome', screenshotOnFailure: false }
    * driver karate.properties['web.url.base']
    * driver.emulateDevice(375, 667, 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1')

Scenario: Landing page
    * configure imageComparison = { mismatchShouldPass: true }
    
    * def loadingScreenshot = screenshot()
    * compareImage { baseline: 'this:screenshots/latest.png', latest: #(loadingScreenshot) }

    * waitFor('.welcome')
    * def loadedScreenshot = screenshot()
    * compareImage { baseline: 'this:screenshots/latest.png', latest: #(loadedScreenshot) }
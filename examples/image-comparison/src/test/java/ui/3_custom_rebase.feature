Feature: Image comparison demo

Background:
    * configure driver = { type: 'chrome', timeout: 5000, screenshotOnFailure: false }
    * driver baseUrl + '?r=0.75'
    * emulateBrowser('phone')

Scenario: Landing page
    * def loadingScreenshot = screenshot()
    * configure imageComparison = { onShowRebase: "(cfg, saveAs) => saveAs('loading.png')", mismatchShouldPass: true }
    * compareImage { baseline: 'this:screenshots/latest.png', latest: #(loadingScreenshot) }

    * waitFor('.welcome')
    * def loadedScreenshot = screenshot()
    * configure imageComparison = { onShowRebase: "(cfg, saveAs) => saveAs('loaded.png')", mismatchShouldPass: true }
    * compareImage { baseline: 'this:screenshots/latest.png', latest: #(loadedScreenshot) }
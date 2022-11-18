Feature: Image comparison demo

Background:
    * configure driver = { type: 'chrome', timeout: 5000, screenshotOnFailure: false }
    * driver baseUrl + '?r=0.1'
    * emulateBrowser('phone')

Scenario: Landing page
    * configure imageComparison = { mismatchShouldPass: true }
    
    * def loadingScreenshot = screenshot()
    * compareImage { latest: #(loadingScreenshot) }

    * waitFor('.welcome')
    * def loadedScreenshot = screenshot()
    * compareImage { latest: #(loadedScreenshot) }

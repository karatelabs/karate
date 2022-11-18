Feature: Image comparison demo

Background:
    * configure imageComparison = { onShowRebase: '(cfg, saveAs) => saveAs(cfg.name)', mismatchShouldPass: true }

    * configure driver = { type: 'chrome', timeout: 5000, screenshotOnFailure: false }
    * driver baseUrl + '?r=1.2'
    * emulateBrowser('phone')

Scenario: Landing page
    * def loadingScreenshot = screenshot()
    * def loadingOpts =
      """
      {
        name: 'loading.png'
      }
      """
    * compareImage { baseline: 'this:screenshots/loading.png', latest: #(loadingScreenshot), options: #(loadingOpts) }

    * waitFor('.welcome')

    * def loadedScreenshot = screenshot()
    * def loadedOpts =
      """
      {
        name: 'loaded.png'
      }
      """
    * compareImage { baseline: 'this:screenshots/loaded.png', latest: #(loadedScreenshot), options: #(loadedOpts) }
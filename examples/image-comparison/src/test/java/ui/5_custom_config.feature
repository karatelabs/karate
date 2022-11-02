Feature: Image comparison demo

Background:
    * configure imageComparison = { onShowRebase: '(cfg, saveAs) => saveAs(cfg.name)' }

    * configure driver = { type: 'chrome', screenshotOnFailure: false }
    * driver karate.properties['web.url.base']
    * driver.emulateDevice(375, 667, 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1')

Scenario: Landing page
    * def loadingScreenshot = screenshot()
    * def loadingOpts =
      """
      {
        name: 'loading.png',
        "ignoredBoxes": [{
            "top": 278,
            "left": 131,
            "bottom": 391,
            "right": 246
        }]
      }
      """
    * compareImage { baseline: 'this:screenshots/loading.png', latest: #(loadingScreenshot), options: #(loadingOpts) }

    * waitFor('.welcome')

    * def loadedScreenshot = screenshot()
    * def loadedOpts =
      """
      {
        name: 'loaded.png',
        "ignoredBoxes": [{
            "top": 73,
            "left": 17,
            "bottom": 125,
            "right": 188
        }]
      }
      """
    * compareImage { baseline: 'this:screenshots/loaded.png', latest: #(loadedScreenshot), options: #(loadedOpts) }
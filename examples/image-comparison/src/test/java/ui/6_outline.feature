Feature: Image comparison demo

Background:
    * configure driver = { type: 'chrome', timeout: 5000, screenshotOnFailure: false }

@setup
Scenario:
    * copy data = browsers

Scenario Outline: Landing page - <deviceType>
    # change to `false` after establishing baseline images... it would be nice if the baseline was created automatically
    * def firstRun = true

    * configure imageComparison = { onShowRebase: '(cfg, saveAs) => saveAs(cfg.name)', mismatchShouldPass: #(firstRun) }
    * driver baseUrl + (firstRun ? '?r=0.75' : '?r=1.5')
    * emulateBrowser('<deviceType>')

    # we have a problem below: the `tablet` device has a different screen size and our `ignoredBoxes` will be off
    * def loadingScreenshot = screenshot()
    * def loadingOpts =
      """
      {
        name: 'loading_<deviceType>.png',
        "ignoredBoxes": [{
            "top": 278,
            "left": 131,
            "bottom": 391,
            "right": 246
        }]
      }
      """
    * def loadingBaselinePath = firstRun ? null : `this:screenshots/${loadingOpts.name}`
    * compareImage { baseline: #(loadingBaselinePath), latest: #(loadingScreenshot), options: #(loadingOpts) }

    * waitFor('.welcome')

    * def loadedScreenshot = screenshot()
    * def loadedOpts =
      """
      {
        name: 'loaded_<deviceType>.png',
        "ignoredBoxes": [{
            "top": 73,
            "left": 17,
            "bottom": 125,
            "right": 188
        }]
      }
      """
    * def loadedBaselinePath = firstRun ? null : `this:screenshots/${loadedOpts.name}`
    * compareImage { baseline: #(loadedBaselinePath), latest: #(loadedScreenshot), options: #(loadedOpts) }

    Examples:
        | karate.setup().data |
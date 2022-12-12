Feature: Image comparison demo

Background:
    # `screenGrab` processes a named image comparison by:
    #   - capturing a screenshot to use as the latest image
    #   - locating the correct baseline image from the filesystem (if one exists)
    #   - fetching comparison options from the filesystem (if they exist)
    #   - automatically copying the latest image to be the new baseline when no baseline exists
    #   - executing `compareImage` using the named baseline image, the latest screenshot, and dynamically-loaded options
    * def screenGrab =
      """
      function (name) {
        const latestBytes = screenshot(false)

        const File = Java.type('java.io.File')
        const latestFile = karate.write(latestBytes, `screenshots/${name}.png`)
        const baselineFile = new File(karate.toAbsolutePath('this:screenshots'), `${name}.png`)
        const optionsFile = new File(karate.toAbsolutePath('this:screenshots/config'), `${name}.json`)

        // read options from browser-specific config JSON (if exists)
        const options = optionsFile.exists() ? karate.read('file:' + optionsFile.getPath()) : {}

        options.baselinePath = baselineFile.getPath()
        options.latestPath = new File(karate.toAbsolutePath('this:' + latestFile.getPath())).getPath()
        options.optionsPath = optionsFile.getPath()

        let baselinePath = 'file:' + options.baselinePath
        if (!baselineFile.exists()) {
          // automatically copy latest image to baseline when no baseline exists
          java.nio.file.Files.copy(latestFile.toPath(), baselineFile.toPath())
          baselinePath = null
        }

        const result = karate.compareImage(baselinePath, latestBytes, options)

        if (result.error && !result.isBaselineMissing) throw new Error(result.error)

        return result
      }
      """

    # instead of manually downloading / managing rebased images we'll provide a shell command to copy/paste
    * def rebaseFn = 'cfg => `cp \\\\\n  ${cfg.latestPath} \\\\\n  ${cfg.baselinePath}`'

    # since we now want the configuration to be stored on the filesystem we'll provide a shell command to copy/paste
    * def configFn = '(imgConfigStr, cfg) => `cat << EOF > ${cfg.optionsPath}\n${imgConfigStr}\nEOF`'

    * configure imageComparison = { mismatchShouldPass: true, onShowRebase: #(rebaseFn), onShowConfig: #(configFn) }

    * configure driver = { type: 'chrome', timeout: 5000, screenshotOnFailure: false }
    * driver baseUrl

@setup
Scenario:
    * copy data = browsers

Scenario Outline: Landing page - <deviceType>
    * emulateBrowser('<deviceType>')
    * screenGrab('loading_<deviceType>')
    * waitFor('.welcome')
    * screenGrab('loaded_<deviceType>')

    Examples:
        | karate.setup().data |
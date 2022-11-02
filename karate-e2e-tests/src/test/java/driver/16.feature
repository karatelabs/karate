Feature:

  Background:
    * driver serverUrl + '/16'

  Scenario:
    # compare screenshot (normally would read a baseline from disk but we'll simulate here)
    * def baselineBytes = screenshot()
    * compareImage { baseline: '#(baselineBytes)', latest: '#(baselineBytes)' }

    # compare mismatched screenshot with ignoredBoxes
    * click('#show')
    * def latestBytes = screenshot()
    * def ignoredBoxes =
    """
    [{
      top: 0,
      bottom: 200,
      left: 0,
      right: 200
    }]
    """
    * compareImage { baseline: '#(baselineBytes)', latest: '#(baselineBytes)', options: { ignoredBoxes: '#(ignoredBoxes)' } }

    # compare mismatched screenshot: allowed to fail with custom failureThreshold
    * compareImage { baseline: '#(baselineBytes)', latest: '#(latestBytes)', options: { failureThreshold: 99.9 } }

    # compare mismatched screenshot: allowed to fail with mismatchShouldPass
    * configure imageComparison = { mismatchShouldPass: true }
    * def result = karate.compareImage(baselineBytes, latestBytes)
    * match result.isMismatch == true

    # missing baseline screenshot: allowed to fail with mismatchShouldPass
    * def result = karate.compareImage(null, latestBytes)
    * match result.isBaselineMissing == true

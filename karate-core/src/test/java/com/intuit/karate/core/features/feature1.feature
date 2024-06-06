Feature:

  Scenario: feature test 1
    * def numOfFeaturesLeft = remainingFeatures()
    * print 'Features left (including this one):', numOfFeaturesLeft
    # this is the first feature that runs, so there should be more than 1 feature running
    * assert numOfFeaturesLeft > 1

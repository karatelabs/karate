Feature:

  Scenario: feature test 2
    * def numOfFeaturesLeft = remainingFeatures()
    * print 'Features left (including this one):', numOfFeaturesLeft
    # there should always be at least 1 feature left, even if it's the current feature running
    * assert numOfFeaturesLeft >= 1

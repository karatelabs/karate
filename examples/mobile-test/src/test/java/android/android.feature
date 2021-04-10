Feature: android test

  Background: App Preset
    * configure driver = { type: 'android', webDriverPath : "/wd/hub", start: false, httpConfig : { readTimeout: 120000 }}

  Scenario: android mobile app UI tests
    Given driver { webDriverSession: { desiredCapabilities : "#(android.desiredConfig)"} }
    And driver.click('#com.bs.droidaction:id/showTextCheckBox')
    And driver.clear('#com.bs.droidaction:id/showTextOnDelay').input("10000")
    And driver.input('#com.bs.droidaction:id/editTextBox', "KarateDSL")
    And driver.click('#com.bs.droidaction:id/showTextCheckBox')
    And retry(10, 1000).waitForAny("#com.bs.droidaction:id/nameTextView", "//android.widget.TextView[@text='KarateDSL']")
    Then match driver.text('#com.bs.droidaction:id/nameTextView') == 'KarateDSL'
    And driver.click('#com.bs.droidaction:id/showTextCheckBox')
    And assert (optional('#com.bs.droidaction:id/nameTextView').present != true)

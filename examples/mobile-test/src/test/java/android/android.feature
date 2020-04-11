Feature: android test

  Background: App Preset
    * configure driver = { type: 'android', webDriverPath : "/wd/hub", start: false, httpConfig : { readTimeout: 120000 }}

  Scenario: android mobile app UI tests
    Given driver { webDriverSession: { desiredCapabilities : "#(android.desiredConfig)"} }
    And driver.input('#com.bs.droidaction:id/editTextBox', "UI demo")
    And driver.click('#com.bs.droidaction:id/clearButton')
    And driver.input('#com.bs.droidaction:id/editTextBox', "Karate DSL")
    Then match driver.text('#com.bs.droidaction:id/nameTextView') == 'Karate DSL'
    And driver.click('#com.bs.droidaction:id/showTextCheckBox')
    And match driver.text('#com.bs.droidaction:id/nameTextView') == ''

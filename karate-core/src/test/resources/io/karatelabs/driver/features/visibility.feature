Feature: Visibility Tests
  Element visibility checks including overlay detection, pointer-events,
  and scroll behavior

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/visibility'

  # ========== Basic Visibility ==========

  Scenario: Visible button is found by wildcard
    * def el = locate('{button}Visible Button')
    * match el.exists() == true

  Scenario: display:none button is not found by wildcard
    * def el = locate('{button}Hidden Button')
    * match el.exists() == false

  Scenario: visibility:hidden button is not found by wildcard
    * def el = locate('{button}Invisible Button')
    * match el.exists() == false

  # ========== Overlay / Modal ==========

  Scenario: Button behind open modal is not found by wildcard
    # Open the modal overlay
    * click('#open-modal')
    * waitFor('#modal')
    # The "Save" button behind the modal should not be resolved by wildcard
    # because it is obscured by the modal backdrop
    # The wildcard should find the modal's Save button instead
    * click('{button}Save')
    * def result = text('#result')
    * match result == 'modal-save clicked'

  Scenario: Button is found again after modal closes
    # Open and close modal
    * click('#open-modal')
    * waitFor('#modal')
    * click('{button}Cancel')
    # Now the behind-modal Save button should be visible again
    * click('#behind-modal')
    * def result = text('#result')
    * match result == 'behind-modal clicked'

  # ========== Pointer Events ==========

  Scenario: pointer-events:none button is not found by wildcard
    * def el = locate('{button}Disabled Pointer')
    * match el.exists() == false

  Scenario: Normal pointer button is found
    * click('{button}Normal Pointer')
    * def result = text('#result')
    * match result == 'normal-pointer clicked'

  # ========== Scroll ==========

  Scenario: Scroll to bottom button
    * scroll('#bottom-button')
    * def el = locate('#bottom-button')
    * match el.exists() == true

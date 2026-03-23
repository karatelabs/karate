Feature: Shadow DOM Tests
  Test that CSS and wildcard locators work across shadow DOM boundaries

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/shadow-dom'

  # ========== Backward Compatibility ==========

  Scenario: Light DOM elements still work
    * match exists('#light-btn') == true
    * def t = text('#light-btn')
    * match t == 'Light Button'

  Scenario: Light DOM click works
    * click('#light-btn')
    * def result = text('#click-result')
    * match result == 'Light clicked'

  Scenario: Light DOM input works
    * input('#light-input', 'hello')
    * def v = value('#light-input')
    * match v == 'hello'

  Scenario: Light DOM wildcard locator works
    * def t = text('{button}Light Button')
    * match t == 'Light Button'

  # ========== Shadow DOM CSS Selectors ==========

  Scenario: CSS selector finds shadow element by aria-label
    * match exists('[aria-label="Shadow Btn"]') == true

  Scenario: Read text from shadow element
    * def t = text('[aria-label="Shadow Btn"]')
    * match t == 'Shadow Click'

  Scenario: Click shadow element via CSS
    * click('[aria-label="Shadow Btn"]')
    * def result = text('#click-result')
    * match result == 'Shadow clicked'

  Scenario: Input into shadow element via CSS
    * input('[aria-label="Shadow Input"]', 'shadow text')
    * def v = value('[aria-label="Shadow Input"]')
    * match v == 'shadow text'

  Scenario: Shadow link exists via CSS
    * match exists('[aria-label="Shadow Link"]') == true

  # ========== Shadow DOM Wildcard Locators ==========

  Scenario: Wildcard locator resolves shadow button
    * def t = text('{button}Shadow Click')
    * match t == 'Shadow Click'

  Scenario: Wildcard click on shadow element
    * click('{button}Shadow Click')
    * def result = text('#click-result')
    * match result == 'Shadow clicked'

  # ========== Nested Shadow Roots ==========

  Scenario: Nested shadow element exists
    * match exists('[aria-label="Nested Shadow Input"]') == true

  Scenario: Click nested shadow button
    * click('{button}Nested Shadow Btn')
    * def result = text('#click-result')
    * match result == 'Nested shadow clicked'

  Scenario: Input into nested shadow element
    * input('[aria-label="Nested Shadow Input"]', 'nested text')
    * def v = value('[aria-label="Nested Shadow Input"]')
    * match v == 'nested text'

  # ========== Mixed Light + Shadow ==========

  Scenario: Mixed page - light element works
    * click('{button}Mixed Light')
    * def result = text('#click-result')
    * match result == 'Mixed light clicked'

  Scenario: Mixed page - shadow element works
    * click('{button}Mixed Shadow')
    * def result = text('#click-result')
    * match result == 'Mixed shadow clicked'

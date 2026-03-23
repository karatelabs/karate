Feature: Mouse Tests
  Mouse operations and positioning

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/input'

  Scenario: Mouse at origin
    * def m = mouse()
    * match m.getX() == 0.0
    * match m.getY() == 0.0

  Scenario: Mouse at coordinates
    * def m = mouse(100, 200)
    * match m.getX() == 100.0
    * match m.getY() == 200.0

  Scenario: Mouse at element
    * def m = mouse('#submit-btn')
    * def pos = position('#submit-btn', true)
    * def expectedX = pos.x + pos.width / 2
    * def expectedY = pos.y + pos.height / 2
    # Allow small tolerance
    * def xDiff = m.getX() - expectedX
    * def yDiff = m.getY() - expectedY
    * assert Math.abs(xDiff) < 2
    * assert Math.abs(yDiff) < 2

  Scenario: Mouse move
    * def m = mouse()
    * m.move(150, 250)
    * match m.getX() == 150.0
    * match m.getY() == 250.0

  Scenario: Mouse offset
    * def m = mouse(100, 100)
    * m.offset(50, 25)
    * match m.getX() == 150.0
    * match m.getY() == 125.0

  Scenario: Mouse click
    * def m = mouse('#submit-btn')
    * m.click()

  Scenario: Mouse double click
    * def m = mouse('#username')
    * m.doubleClick()

  Scenario: Mouse right click
    * def m = mouse('#username')
    * m.rightClick()

  Scenario: Mouse method chaining
    * def m = mouse().move(100, 100).offset(50, 50).click()
    * match m.getX() == 150.0
    * match m.getY() == 150.0

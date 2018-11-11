@ignore
Feature: browser automation

Background:
  * configure driver = { type: 'chrome', start: true }
  # * configure driver = { type: 'chromedriver', start: true }
  # * configure driver = { type: 'geckodriver', start: true }  
  # * configure driver = { type: 'safaridriver', start: true }
  # * configure driver = { type: 'mswebdriver', start: true }

Scenario: try to login to github
    and then do a google search

  Given driver 'https://github.com/login'
  And driver.input('#login_field', 'hello')
  And driver.input('#password', 'world')
  When driver.submit("//input[@name='commit']")
  Then match driver.html('#js-flash-container') contains 'Incorrect username or password.'
  
  Given driver 'https://google.com'
  And driver.input("//input[@name='q']", 'karate dsl')
  When driver.submit("//input[@name='btnI']")
  Then match driver.location == 'https://github.com/intuit/karate'

  * def bytes = driver.screenshot()
  * eval karate.embed(bytes, 'image/png')
   
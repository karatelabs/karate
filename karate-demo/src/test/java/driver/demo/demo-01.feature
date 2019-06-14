Feature: browser automation

Background:
  # * configure driver = { type: 'chrome' }
  # * configure driver = { type: 'chromedriver', showDriverLog: true }
  * configure driver = { type: 'geckodriver', showDriverLog: true }
  # * configure driver = { type: 'safaridriver' }
  # * configure driver = { type: 'mswebdriver' }

Scenario: try to login to github
    and then do a google search

  Given driver 'https://github.com/login'
  And driver.input('#login_field', 'hello')
  And driver.input('#password', 'world')
  When driver.submit("input[name=commit]")
  Then match driver.html('#js-flash-container') contains 'Incorrect username or password.'
  
  Given driver 'https://google.com'
  And driver.input("input[name=q]", 'karate dsl')
  When driver.submit("input[name=btnI]")
  Then match driver.location == 'https://github.com/intuit/karate'

  * def bytes = driver.screenshot()
  * karate.embed(bytes, 'image/png')
   
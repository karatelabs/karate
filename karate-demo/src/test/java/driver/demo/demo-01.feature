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
  And input('#login_field', 'hello')
  And input('#password', 'world')
  When submit("input[name=commit]")
  Then match html('#js-flash-container') contains 'Incorrect username or password.'
  
  Given driver 'https://google.com'
  And input("input[name=q]", 'karate dsl')
  When submit("input[name=btnI]")
  Then match driver.location == 'https://github.com/intuit/karate'

  * def bytes = driver.screenshot()
  * karate.embed(bytes, 'image/png')
   
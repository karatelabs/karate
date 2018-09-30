@ignore
Feature: browser automation

Background:
  * configure driver = { type: 'chrome', executable: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' }
  # * configure driver = { type: 'chromedriver', port: 9515, executable: '/Users/pthomas3/dev/webdriver/chromedriver' }

Scenario: try to login to github
    and then do a google search

  Given location 'https://github.com/login'
  And input #login_field = 'hello'
  And input #password = 'world'
  When submit //input[@name='commit']
  And match driver.html('#js-flash-container') contains 'Incorrect username or password.'
  
  Given location 'https://google.com'
  And input //input[@name='q'] = 'karate dsl'
  When submit //input[@name='btnI']
  And match driver.location == 'https://github.com/intuit/karate'
   
@ignore
Feature: browser automation

Background:
  * configure webDriver = { executable: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' }

Scenario: try to login to github
    and then do a google search

  Given browse 'https://github.com/login'
  And type #login_field = 'hello'
  And type #password = 'world'
  When submit //input[@name='commit']
  Then def html = karate.browser.html('#js-flash-container')
  And match html contains 'Incorrect username or password.'
  
  Given browse 'https://google.com'
  And type //input[@name='q'] = 'karate dsl'
  When submit //input[@name='btnI']
  And def location = karate.browser.url
  And match location == 'https://github.com/intuit/karate'
   
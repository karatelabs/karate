Feature:

Scenario: try to login to github
  * configure driver = { type: 'chrome', showDriverLog: true }
  * driver 'https://github.com/login'
  * screenshot()
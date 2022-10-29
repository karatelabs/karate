Feature:

Scenario: try to login to github
  * configure driver = { type: 'noopdriver', showDriverLog: true }
  * driver 'https://google.com'
  * configUtils.existsFunction('#element')
  * screenshot()
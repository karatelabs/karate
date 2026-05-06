@cdp
Feature: PDF Generation
  CDP-only — relies on Page.printToPDF which W3C WebDriver doesn't expose.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/'
    * waitFor('h1')

  Scenario: pdf() returns bytes for default options
    * def bytes = pdf()
    * match bytes == '#notnull'
    * assert bytes.length > 0

  Scenario: pdf({...}) accepts CDP options map
    * def bytes = pdf({ printBackground: true })
    * match bytes == '#notnull'
    * assert bytes.length > 0

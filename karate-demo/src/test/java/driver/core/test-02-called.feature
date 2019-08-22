@ignore
Feature: common driver init code

Scenario:
  * configure driver = { type: 'chrome', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-01'

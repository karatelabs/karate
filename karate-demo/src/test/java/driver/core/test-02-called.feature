@ignore
Feature: common driver init code

Scenario:
  * configure driver = { type: 'chrome', start: true }
  * def webUrlBase = karate.properties['web.url.base']
  * location webUrlBase + '/page-01'


    
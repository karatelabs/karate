@ignore
Feature: common driver init code

Scenario:
  * configure driver = { type: 'chrome' }
  * def webUrlBase = karate.properties['web.url.base']
  * driver webUrlBase + '/page-01'


    
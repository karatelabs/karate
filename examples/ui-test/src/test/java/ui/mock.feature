@ignore
Feature:

Background:
  * configure responseHeaders = { 'Content-Type': 'text/html; charset=utf-8' }

Scenario: pathMatches('/page-01')
  * def response = read('page-01.html')

Scenario: pathMatches('/karate.js')
  * def responseHeaders = { 'Content-Type': 'text/javascript; charset=utf-8' }
  * def response = karate.readAsString('karate.js')

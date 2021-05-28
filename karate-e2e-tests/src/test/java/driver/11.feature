Feature: 

Background:
* driver serverUrl + '/11'

Scenario:

* click("#helloDiv")
* switchPage('/11_tab')
* karate.log("driver.url", driver.url)
* match driver.url contains '11_tab'
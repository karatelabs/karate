Feature: 

Background:
* driver serverUrl + '/11'

Scenario:
* click("#helloDiv")
* switchPage('/11_tab')
* waitForUrl('/11_tab')
* match text('#content') == 'Page 11 Tab'
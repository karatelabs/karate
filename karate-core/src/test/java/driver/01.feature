Feature:

Background:
* driver serverUrl
* click('{}01')

Scenario:
# assert page url
* match driver.url == serverUrl + '/01'

# assert page title
* match driver.title == 'Page 01'

# browser navigation: back
* back()
* match driver.title == 'Driver Tests Home Page'

# browser navigation: forward
* forward()
* match driver.title == 'Page 01'

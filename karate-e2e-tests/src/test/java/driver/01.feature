Feature:

Background:
* driver serverUrl + '/01'

Scenario:
# assert page url
* match driver.url == serverUrl + '/01'

# assert page title
* match driver.title == 'Page 01'

# before refresh()
* match text('#pageLoadCount') == '1'

# refresh page
* refresh()
* match text('#pageLoadCount') == '2'

# reload page
* refresh()
* match text('#pageLoadCount') == '3'

# navigate to new page
* click('a')

# wait for new page to load
* waitForUrl(serverUrl + '/02')

# assert for driver title
* match driver.title == 'Page 02'

# browser navigation: back
* back()

# wait for page to re-load
* waitForUrl(serverUrl + '/01')

* match driver.title == 'Page 01'

# browser navigation: forward
* forward()

# wait for page to re-load
* waitForUrl(serverUrl + '/02')

* match driver.title == 'Page 02'

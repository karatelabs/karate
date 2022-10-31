Feature:

  Background:
    * driver serverUrl + '/17_a'

  Scenario:
    * def openPageInNewTab = (pageUrl) => driver.script(`window.open('${pageUrl}', '_blank', 'noopener')`)

    * waitFor('input[name=a]').input('a1')

    * openPageInNewTab(serverUrl + '/17_b')
    * switchPage('17_b')
    * waitFor('input[name=b]').input('b1')
    * close()

    * switchPage('17_a')
    * input('input[name=a]', 'a2')

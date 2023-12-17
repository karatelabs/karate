Feature:

  Background:
 
  Scenario: Retry

    * driver serverUrl + '/02'
    * def start = new Date().getTime();
# wait for slow loading element
    * retry(3, 1500).click('#slowDiv')
    * def end = new Date().getTime();
    * def elapsedTime = end - start
    * print "Elapsed time:", elapsedTime
# setTimeout in the html page kicked off when the page was loaded, which is a few (50-ish) ms before we get here.
# Since slowDiv takes 2s to load, click() should return not faster than 2s - 50ms, and not longer than 2s + some arbitrary buffer 
    * assert elapsedTime > 1550
    * assert elapsedTime < 3500    

  Scenario: submit

    * driver serverUrl + '/18'
    * submit().click('#slowlink')
    * match optional('#containerDiv').present == true
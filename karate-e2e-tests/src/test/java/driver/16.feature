Feature:

  Background:
  * driver serverUrl + '/16'

Scenario:
  Given def ulList = locateAll( "//ul")
  And def liList = locateAll( "//li")
  Then match ulList == '#[_ == 2]'
  And match liList == '#[_ == 8]'
  And match ulList[0].locateAll( "/li") == '#[_ == 5]'
  And match ulList[0].locateAll( "//li") == '#[_ == 8]'
  And match ulList[1].locateAll( "/li") == '#[_ == 3]'
  And match ulList[1].locateAll( "//li") == '#[_ == 3]'
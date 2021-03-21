Feature: 

Background:
* driver serverUrl + '/09'

Scenario: friendly locators
* def parent = waitFor('.div-02')
# make sure the find-by-text works relative to search node and not from document root
* def found = parent.locate('{}Some Text')
* match found.attribute('class') == 'div-04'

# find all, exact match
* def list1 = locateAll('{}Some Text')
* assert list1.length == 2

# find all, contains match
* def list2 = locateAll('{^}Text')
* assert list2.length == 2
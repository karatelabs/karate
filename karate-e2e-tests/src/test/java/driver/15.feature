Feature:

Background:
* driver serverUrl + '/15'

Scenario:
# xpath script in whole document
* def first = script('//li', '_.textContent')
* match first == 'item 1'

# xpath scriptAll in whole document
* def list = scriptAll('//li', '_.textContent')
* match list == ['item 1', 'item 2', 'item 2.1', 'item 2.2', 'item 3']

# get first ul in entire document
* def first = locate('//ul')
# validates that operations other than locate also work...
* match attribute('//ul', 'id') == 'first'

# relative xpath - locate
* def second = first.locate('./ul/li')
* match second.text == 'item 2.1'

# relative xpath - locate all
* def seconds = second.locateAll('..//li')
* assert seconds.length == 2
* match (seconds[1].text) == 'item 2.2'

# relative xpath - script all
* match first.locateAll('./li').length == 3
* def level1 = first.scriptAll('./li', '_.textContent')
* match level1 == ['item 1', 'item 2', 'item 3']

# relative xpath (any depth) script all
* def all = first.scriptAll('.//li', '_.textContent')
* match all == ['item 1', 'item 2', 'item 2.1', 'item 2.2', 'item 3']

* def children = first.children
* match (children.length) == 4
* match (children[0].text) == 'item 1'
* match (children[0].parent).attribute('id') == 'first'
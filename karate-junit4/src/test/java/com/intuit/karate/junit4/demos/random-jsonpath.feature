Feature:

Scenario:

* def data = read('data.json')
# wrapping in parantheses forces evaluation as a JS expression
* def size1 = (data.Contents.Contents.length)
# the 'get' keyword forces pure json path
* def size2 = get data.Contents.Contents.length()
* assert size1 == size2
* def index = Math.floor(Math.random() * size1)
* print 'selected index: ' + index
* def item = (data.Contents.Contents[index])
* def children_uuids = item.children_uuids
* match children_uuids == { item: ['item1_uuid', 'item2_uuid'] }

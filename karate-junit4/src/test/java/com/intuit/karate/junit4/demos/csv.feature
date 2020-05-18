Feature:

Scenario:
* def data = [{ foo: 'bar1', baz: 'ban1' }, { foo: 'bar2', baz: 'ban2' }]
* string temp = karate.toCsv(data)
* print temp

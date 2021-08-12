Feature:

Scenario:
* def schema = "#[] read('schema-read.json')"
* print schema
* match [] == schema
* match [{ foo: 'bar', items: [] }] == schema
* match [{ foo: 'bar', items: [{ a: 1 }] }] == schema

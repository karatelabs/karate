Feature:

  Scenario:
    * def schema = "#[] read('schema-read.json')"
    * def response = [{ foo: 'bar', items: [{ a: 1 }] }]
    * match response == schema
    * configure matchEachEmptyAllowed = true
    * def response = [{ foo: 'bar', items: [] }]
    * match response == schema
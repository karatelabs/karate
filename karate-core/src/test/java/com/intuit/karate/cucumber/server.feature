@ignore
Feature:

Scenario:
* def cats = []
* def cat = request
* set cat.id = '12345'
* set cats[0] = cat
* def response = cat

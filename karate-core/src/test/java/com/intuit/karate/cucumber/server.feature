@ignore
Feature:

Scenario:
* def cats = []
* def cat = request
* set cat.id = '12345'
* set cats[] = cat
* def response = cat

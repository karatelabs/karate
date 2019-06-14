Feature: example of js java hybrid sort

Scenario: case-insensitive sort
    * def ArrayList = Java.type('java.util.ArrayList')
    * def Collections = Java.type('java.util.Collections')
    * def json = [{ v: 'C' }, { v: 'b' }, { v: 'A' }]
    * def actual = $json[*].v
    * match actual == ['C', 'b', 'A']
    * def list = new ArrayList()
    * karate.repeat(actual.length, function(i){ list.add(actual[i]) })
    * match list == ['C', 'b', 'A']
    * Collections.sort(list, java.lang.String.CASE_INSENSITIVE_ORDER)
    * match list == ['A', 'b', 'C']

Scenario: the example above implemented using a re-usable js function
    * def json = [{ v: 'C' }, { v: 'b' }, { v: 'A' }]
    * def actual = $json[*].v
    * match actual == ['C', 'b', 'A']
    * def sorted = call read('sort.js') actual
    * match sorted == ['A', 'b', 'C']


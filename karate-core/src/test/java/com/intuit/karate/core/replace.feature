Feature: replace keyword

Scenario: one-line default placeholder

* def text = 'hello <foo> world'
* replace text.foo = 'bar'
* match text == 'hello bar world'


Scenario: one-line non-default placeholder 1

* def text = 'hello ${foo} world'
* replace text.${foo} = 'bar'
* match text == 'hello bar world'


Scenario: one-line non-default placeholder 2

* def text = 'hello @@foo@@ world'
* replace text.@@foo@@ = 'bar'
* match text == 'hello bar world'


Scenario: table default placeholder

* def text = 'hello <one> world <two> bye'

* replace text
    | token | value   |
    | one   | 'cruel' |
    | two   | 'good'  |

* match text == 'hello cruel world good bye'


Scenario: table non-default placeholder

* def text = 'hello ${one} world @@two@@ bye'

* replace text
    | token   | value   |
    | ${one}  | 'cruel' |
    | @@two@@ | 'good'  |

* match text == 'hello cruel world good bye'


Scenario: table with expression evaluation

* def text = 'hello <one> world <two> bye'
* def first = 'cruel'
* def second = 'good'

* replace text
    | token | value  |
    | one   | first  |
    | two   | second |

* match text == 'hello cruel world good bye'


Scenario: table with complex expression evaluation

* def text = 'hello <one> world <two> bye'
* def json = { first: 'cruel', second: 'good' }

* replace text
    | token | value       |
    | one   | json.first  |
    | two   | json.second |

* match text == 'hello cruel world good bye'

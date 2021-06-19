Feature:

Background:
* def isNot = function(x) { return function(row) { return row.id != x } }
* callonce read('call-once-from-feature.feature')

Scenario Outline:
* call read('called.feature')
* assert id != '0'

Examples:
| karate.filter(read('data.csv'), isNot('0')) |

Scenario Outline:
* call read('called.feature')
* assert id != '1'

Examples:
| karate.filter(read('data.csv'), isNot('1')) |

Scenario Outline:
* call read('called.feature')
* assert id != '2'

Examples:
| karate.filter(read('data.csv'), isNot('2')) |

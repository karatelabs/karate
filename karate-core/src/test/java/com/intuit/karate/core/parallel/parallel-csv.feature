Feature:

@setup
Scenario:
* def data = read('data.csv')
* def exclude = karate.wrapFunction(x => data.filter(y => y.id != x))
* def include = karate.wrapFunction(x => data.filter(y => y.id == x))

Scenario Outline:
* assert id != '0'

Examples:
| karate.setup().data.filter(x => x.id != '0') |

Scenario Outline:
* assert id != '1'

Examples:
| karate.setup().exclude('1') |

Scenario Outline:
* assert id == '2'

Examples:
| karate.setup().include('2') |

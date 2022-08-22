Feature:

@setup
Scenario:
* def data = read('data.csv')
* def exclude = x => data.filter(y => y.id != x)
* def include = x => data.filter(y => y.id == x)

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

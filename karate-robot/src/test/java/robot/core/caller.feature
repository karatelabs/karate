Feature:

Background:
#* def utils = call read('')
* def data = [{a: 1},{a: 2}]
* robot { window: '^Chrome' }

Scenario Outline:
* print __num
* screenshot()

Examples:
| data |



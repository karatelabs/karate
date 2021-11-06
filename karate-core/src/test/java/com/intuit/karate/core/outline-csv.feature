Feature:

Scenario Outline: first column is <test>
* match __row == { test: '#string', birthDate: '#string' }

Examples:
| read('outline-csv.csv') |

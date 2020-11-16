Feature:

Background:
* def generator = function(i){ if (i == 5) return null; return { name: 'cat' + i, age: i } }

Scenario Outline:
* match __num == age
* match __row.name == 'cat' + age

Examples:
| generator |

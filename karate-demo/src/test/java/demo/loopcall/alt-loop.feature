Feature: loop.feature - but using a scenario outline

Background:
* def cat = { name: 'Tom' }

Scenario Outline:
* def type = <type>
* def res1 = call read('create-' + type + '.feature') cat
* def res2 = call read('result.feature') { id: '#(res1.id)' }
* def response = res2.response
* match response == { id: '#(res1.id)' , name: 'Tom' }

Examples:
| type |
| json |
| xml  |

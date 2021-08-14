Feature:

Scenario: in-line csv
* csv data =
"""
first,second
a1,a2
b1,b2
"""
* match data == [{ first: 'a1', second: 'a2'}, { first: 'b1', second: 'b2' }]

Scenario: in-line yaml
* yaml data =
"""
name: John
input:
  id: 1
  subType: 
    name: Smith
    deleted: false
"""
* match data ==
"""
{
  name: 'John',
  input: { 
    id: 1,
    subType: { name: 'Smith', deleted: false }    
  }
}
"""


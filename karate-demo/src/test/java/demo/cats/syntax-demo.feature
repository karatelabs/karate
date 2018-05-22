Feature: karate syntax

Scenario: demo of json handling

* def billie =
"""
{
  name: 'Billie',
  kittens: [
      { id: 23, name: 'Bob' },
      { id: 42, name: 'Wild' }
  ]
} 
"""
* match billie.kittens contains { id: 42, name: 'Wild' }

* match billie.kittens contains { id: '#? _ > 25', name: '#string' }

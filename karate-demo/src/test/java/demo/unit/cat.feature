Feature: demo calling java methods with complex types

  Background:
    * call read('common.feature')

  Scenario: using constructor and setters
    * def billie = new Cat()
    * billie.id = 1
    * billie.name = 'Billie'
    * match toJson(billie) == { id: 1, name: 'Billie' }

  Scenario: using json and calling java methods
    * def billie = toCat({ id: 1, name: 'Billie' })
    * def bob = toCat({ id: 2, name: 'Bob' })
    * def wild = toCat({ id: 3, name: 'Wild' })
    * billie.addKitten(bob)
    * billie.addKitten(wild)
    * match toJson(billie) ==
      """
      {
        id: 1, name: 'Billie', kittens: [
          { id: 2, name: 'Bob' },
          { id: 3, name: 'Wild' }
        ]
      }
      """

  Scenario Outline: data driven
    * def billie = toCat({ id: 1, name: 'Billie' })
    * def fun = function(n, i){ return { id: i + 2, name: n } }
    * def kittens = karate.map(names, fun)
    * billie.kittens = kittens
    * match toJson(billie) contains expected
    * match toJson(billie).kittens == expected.kittens

    Examples:
      | names!          | expected!           |
      | ['Bob', 'Wild'] | { kittens: '#[2]' } |
      | ['X', 'Y', 'Z'] | { kittens: '#[3]' } |

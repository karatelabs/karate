Feature: demo calling java methods with complex types
  note how easy it is to construct complex java objects from json or manually

  Background:
    # this common feature sets up glue code / fixtures for java code
    * call read('common.feature')

  Scenario: calling a setter on a java object
    * def billie = toCat({ id: 1, name: 'Billie' })
    * def bob = toCat({ id: 2, name: 'Bob' })
    * def wild = toCat({ id: 3, name: 'Wild' })
    * eval billie.kittens = [bob, wild]
    * match toJson(billie) == { id: 1, name: 'Billie', kittens: [{ id: 2, name: 'Bob' }, { id: 3, name: 'Wild' }] }

  Scenario: calling a method on a java object
    * def billie = toCat({ id: 1, name: 'Billie' })
    * def bob = toCat({ id: 2, name: 'Bob' })
    * def wild = toCat({ id: 3, name: 'Wild' })
    * eval billie.addKitten(bob)
    * eval billie.addKitten(wild)
    * match toJson(billie) == { id: 1, name: 'Billie', kittens: [{ id: 2, name: 'Bob' }, { id: 3, name: 'Wild' }] }

  Scenario Outline: data driven
    * def billie = toCat({ id: 1, name: 'Billie' })
    * def fun = function(n, i){ return { id: ~~(2 + i), name: n } }
    * def kittens = karate.map(names, fun)
    * eval billie.kittens = kittens
    * match toJson(billie) contains expected

    Examples:
      | names!          | expected!            |
      | ['Bob', 'Wild'] | { kittens: '#[2]' }  |
      | ['X', 'Y', 'Z'] | { kittens: '#[3]' }  |


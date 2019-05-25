Feature: demo calling java methods with complex types
  note how easy it is to construct complex java objects from json or manually

  Background:
    # this common feature sets up glue code / fixtures for java code
    * call read('common.feature')

  Scenario:
    * def billie = toCat({ id: 1, name: 'Billie' })
    * def bob = toCat({ id: 2, name: 'Bob' })
    * def wild = toCat({ id: 3, name: 'Wild' })
    * eval billie.kittens = [bob, wild]
    * match toJson(billie) == { id: 1, name: 'Billie', kittens: [{ id: 2, name: 'Bob' }, { id: 3, name: 'Wild' }] }

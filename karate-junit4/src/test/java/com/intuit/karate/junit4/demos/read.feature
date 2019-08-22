Feature: reading files

Scenario: relative path
  * def fun = read('../syntax/for-demos.js')
  * assert fun() == 'foo'

Scenario: def call read
  # this is exactly the same as read('cats.json')
  * def cats = call read 'cats.json'
  * match cats == [{ name: 'Bob', age: 5 }, { name: 'Nyan', age: 7 }]

Scenario: call read (global)
  * call read 'cat.json'
  * match name == 'Bob'
  * match age == 5

Scenario: set read (global)
  * karate.set(read('cat.json'))
  * match name == 'Bob'
  * match age == 5




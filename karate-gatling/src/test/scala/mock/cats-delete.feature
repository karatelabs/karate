Feature: delete all cats found

  Background:
    * url karate.properties['mock.cats.url']

  Scenario: get all cats and then delete each by id
    When method get
    Then status 200

    * def delete = read('cats-delete-one.feature')
    * def result = call delete response

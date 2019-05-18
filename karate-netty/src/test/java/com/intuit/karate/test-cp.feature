Feature: test classpath
    java -jar karate.jar -cp src/test/java src/test/java/com/intuit/karate/test-cp.feature

Scenario: relative adjacent
  * def temp = read('test-cp2.json')
  * match temp == { success: true }

Scenario: classpath root
  * def temp = read('classpath:test-cp1.json')
  * match temp == { success: true }

Scenario: classpath deep
  * def temp = read('classpath:com/intuit/karate/test-cp2.json')
  * match temp == { success: true }

Scenario: file 1
  * def temp = read('file:src/test/java/test-cp1.json')
  * match temp == { success: true }

Scenario: file 2
  * def temp = read('file:src/test/java/com/intuit/karate/test-cp2.json')
  * match temp == { success: true }

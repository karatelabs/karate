Feature: test working directory edge cases

Scenario: a called script still knows what the working directory is

* def text = call read('classpath:file-utils.js') 'relative.txt'
* match text == 'hello world'


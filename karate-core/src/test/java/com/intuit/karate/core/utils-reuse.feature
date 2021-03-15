Feature: json and js functions loaded in the karate-config.js

Scenario: function re-use, global / shared scope
    * call read('config-reuse-common.feature')
    * assert hello() == 'hello'
    * assert world() == 'world'

Scenario: function re-use, isolated / name-spaced scope
    * def utils = read('config-reuse-common.feature')
    * assert utils.hello() == 'hello'
    * assert utils.world() == 'world'

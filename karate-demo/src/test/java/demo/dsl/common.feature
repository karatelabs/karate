@ignore
Feature:

Scenario:
# in-line js function
* def quack = function(){ karate.log('quack!') }

# js function from file
* def greet = read('greet.js')

# feature from file
* def login = read('login.feature')

Feature:

Scenario: reading json 1
    * def fun = function(){ var temp = read('js-read.json'); return temp.error[1].id }
    * def val = call fun
    * match val == 2

Scenario: reading json 2
    * def fun = function(){ var temp = karate.read('js-read.json'); return temp.error[1].id }
    * def val = call fun
    * match val == 2
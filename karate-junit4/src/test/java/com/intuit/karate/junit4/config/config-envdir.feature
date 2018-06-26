@ignore
Feature: config over-ride per environment

Scenario: check if conf/karate-config-confenvdir.js was invoked
    * match confoverride == 'success'
    * match baseconfig == 'loaded' 

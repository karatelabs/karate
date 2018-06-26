@ignore
Feature: config over-ride per environment

Scenario: check if classpath:karate-config-confdemo.js was invoked
    * match confoverride == 'yes'
    * match baseconfig == 'loaded' 

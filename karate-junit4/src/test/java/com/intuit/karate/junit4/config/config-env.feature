@ignore
Feature: config over-ride per environment

Scenario: check if .karate/karate-config-confdemo.js was invoked
    * match confoverride == 'success'

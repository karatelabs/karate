Feature: an edge case for com.intuit.karate.MatchStep

Scenario:
* def response = { "message": "A message with the word contains included" }
* match response == { "message": "A message with the word contains included" }
* match response ==
"""
{ "message": "A message with the word contains included" }
"""

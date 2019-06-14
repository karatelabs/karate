@ignore
Feature: oauth1 example
    which is just example code as we couldn't find an online sandbox to test
    contributions welcome !

Background:
    * url demoBaseUrl

Scenario:
    * def Signer = Java.type('demo.oauth.Signer')    
    * def params =
    """
    { 
      'userId': '399645532', 
      'os':'android', 
      'client_key': '3c2cd3f3',
      'token': '141a649988c946ae9b5356049c316c5d-838424771',
      'token_client_salt': 'd340a54c43d5642e21289f7ede858995'
    }
    """
    * Signer.sign('382700b563f4', params)
    * path 'echo'
    * form fields params
    * method post
    * status 200
    

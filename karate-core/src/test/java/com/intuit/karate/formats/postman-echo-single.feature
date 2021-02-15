Feature: 

Scenario: OAuth1.0 Verify Signature
Given url 'https://echo.getpostman.com/oauth1'
And header Authorization = 'OAuth oauth_consumer_key="RKCGzna7bv9YD57c",oauth_signature_method="HMAC-SHA1",oauth_timestamp="1442394747",oauth_nonce="UIGipk",oauth_version="1.0",oauth_signature="CaeyGPr2mns1WCq4Cpm5aLvz6Gs="'
And request [{"key":"code","value":"xWnkliVQJURqB2x1","type":"text","enabled":true},{"key":"grant_type","value":"authorization_code","type":"text","enabled":true},{"key":"redirect_uri","value":"https:\/\/www.getpostman.com\/oauth2\/callback","type":"text","enabled":true},{"key":"client_id","value":"abc123","type":"text","enabled":true},{"key":"client_secret","value":"ssh-secret","type":"text","enabled":true}]
When method GET


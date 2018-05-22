@ignore
Feature: oauth 2 test using
    http://brentertainment.com/oauth2

Background:
* url 'http://brentertainment.com/oauth2/lockdin'

Scenario: oauth 2 flow

* path 'token'
* form field grant_type = 'password'
* form field client_id = 'demoapp'
* form field client_secret = 'demopass'
* form field username = 'demouser'
* form field password = 'testpass'
* method post
* status 200

* def accessToken = response.access_token

* path 'resource'
* header Authorization = 'Bearer ' + accessToken
# * param access_token = accessToken
* method get
* status 200

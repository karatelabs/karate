Feature: 

Scenario: Request Headers
Given url 'https://echo.getpostman.com/headers'
And header my-sample-header = 'Lorem ipsum dolor sit amet'
When method GET


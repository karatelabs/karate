Feature: payment service

Scenario:
    Given url paymentServiceUrl
    And path 'pay'
    When method get
    Then status 200
    And match response == { success: true }

Feature: Test reporting

	@report_demo
  Scenario Outline: Generate html report
    Given url 'http://services.groupkt.com/country/get', "iso2code",'<country_code>'
     When method get
     Then status 200
      And def result = response.RestResponse.result
      And match result.name contains <expected>
      And print result.alpha3_code, <expected>

    Examples: 
      | country_code | expected                   |
      | IN           | "India"                    |
      | US           | "United States of America" |
      | AU           | "Australia"                |

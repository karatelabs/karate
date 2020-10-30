Feature: schema generated test

  Background:
    * string productStructure = read('product-structure.json')
    * def generator = function(i){ if (i == 20) return null; return {"product": new faker().generate(productStructure) }; }

  Scenario Outline: using json schema generated request
    * def warehouseLocationStructureValidation = { latitude: '#number', longitude: '#number' }
    * def productStructureValidation =
    """
    {
      id: '#number',
      name: '#string',
      price: '#number? _ > 0',
      tags: '##[_ > 0] #string',
      dimensions: {
        length: '#number',
        width: '#number',
        height: '#number'
      },
      warehouseLocation: '##(warehouseLocationStructureValidation)'
    }
    """

    Given url demoBaseUrl
    And path 'products'
    And request <product>
    When method post
    Then status 200
    And match response == '#(productStructureValidation)'

    Examples:
      | generator |

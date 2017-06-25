Feature: json schema validation

Scenario: using a third-party lib and a schema file

* string schema = read('products-schema.json')
* string json = read('products.json')
* def SchemaUtils = Java.type('demo.schema.SchemaUtils')
* assert SchemaUtils.isValid(json, schema)


Scenario: using karate's simpler alternative to json-schema

* def warehouseLocation = { latitude: '#number', longitude: '#number' }
* def productStructure =
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
  warehouseLocation: '##(warehouseLocation)'
}
"""
* def json = read('products.json')
* match json == '#[] productStructure'
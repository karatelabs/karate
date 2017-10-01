Feature:

Scenario:

Given def temperature = { celsius: 100, fahrenheit: 212 }
Then match temperature contains { fahrenheit: '#? _ == $.celsius * 1.8 + 32' }
# using embedded expressions
Then match temperature == { celsius: '#number', fahrenheit: '#($.celsius * 1.8 + 32)' }

Given def json =
"""
{
  "hotels": [
    { "roomInformation": [{ "roomPrice": 618.4 }], "totalPrice": 618.4  },
    { "roomInformation": [{ "roomPrice": 679.79}], "totalPrice": 679.79 }
  ]
}
"""
Then match each json.hotels contains { totalPrice: '#? _ == _$.roomInformation[0].roomPrice' }
# using embedded expressions
Then match each json.hotels == { roomInformation: '#array', totalPrice: '#(_$.roomInformation[0].roomPrice)' }
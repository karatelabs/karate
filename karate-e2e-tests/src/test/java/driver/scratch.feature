Feature:

Scenario:
* driver serverUrl + '/01'
* match driver.url == serverUrl + '/01'
* match (driver.dimensions) contains { x: '#number', y: '#number', width: '#number', height: '#number' }

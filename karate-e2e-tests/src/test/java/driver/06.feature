Feature:

Background:
* driver serverUrl + '/06'

Scenario:
* def loc1 = position('#first')
* match loc1 contains { x: '#number', y: '#number', width: '#number', height: '#number' }
* def loc2 = position('#second')
* match loc1.y == loc2.y
* match loc1.width == 102
* match loc1.width == loc2.width
Feature: Java values in scripts behave as Java objects with a JS face

Background:
* def Instant = Java.type('java.time.Instant')
* def LocalDate = Java.type('java.time.LocalDate')
* def JDate = Java.type('java.util.Date')
* def ArrayList = Java.type('java.util.ArrayList')
* def JMap = Java.type('java.util.Map')

Scenario: java.time members resolve to Java before Date.prototype
* def iso = Instant.parse('2026-10-26T21:19:40.123Z').toString()
* match iso == '2026-10-26T21:19:40.123Z'
* def cid = JMap.of('payload', JMap.of('cid', 'client-123')).get('payload').get('cid')
* match cid == 'client-123'

Scenario: a LocalDate is a Java object that strings and subtracts like a date
* def ld = LocalDate.of(2026, 10, 26)
* match ld.getYear() == 2026
* def month = ld.getMonth() + ''
* match month == 'OCTOBER'
* match ld.plusDays(1).toString() == '2026-10-27'
* def concat = '' + ld
* match concat == '2026-10-26'
* def str = String(ld)
* match str == '2026-10-26'
* def tpl = `${ld}`
* match tpl == '2026-10-26'
* def eq = ld == '2026-10-26'
* match eq == true
* def a = LocalDate.of(2026, 6, 10)
* def b = LocalDate.of(2026, 6, 11)
* def diff = b - a
* match diff == 86400000
* def before = a < b
* match before == true
* match ld.getFullYear() == 2026

Scenario: java dates serialize to ISO strings
* def ld = LocalDate.of(2026, 10, 26)
* def inst = Instant.parse('2026-10-26T21:19:40.123Z')
* def data = { d: '#(ld)', i: '#(inst)' }
* def json = JSON.stringify(data)
* match json == '{"d":"2026-10-26","i":"2026-10-26T21:19:40.123Z"}'
* def jud = new JDate(0)
* def root = JSON.stringify(jud)
* match root == '"1970-01-01T00:00:00.000Z"'
# karate's own serializer (string keyword, match messages, request bodies) agrees
* string text = data
* match text == '{"d":"2026-10-26","i":"2026-10-26T21:19:40.123Z"}'

Scenario: a java.util.Date is the JS Date's mirror and JS setters write through
* def jud = new JDate(0)
* def copy = new Date(jud)
* copy.setTime(5000)
* match jud.getTime() == 0
* jud.setTime(1000)
* jud.setUTCSeconds(2)
* match jud.toInstant().toEpochMilli() == 2000
* def isDate = jud instanceof Date
* match isDate == true
* def ldIsDate = LocalDate.of(2026, 10, 26) instanceof Date
* match ldIsDate == false

Scenario: a Java list is a JS array
* def list = new ArrayList()
* list.add('a')
* list.add('b')
* def concat = '' + list
* match concat == 'a,b'
* def isArray = Array.isArray(list)
* match isArray == true
* def isInstance = list instanceof Array
* match isInstance == true

Feature: xml and xpath demos

Scenario: get for complex things such as xpath functions

* def foo =
"""
<records>
  <record index="1">a</record>
  <record index="2">b</record>
  <record index="3" foo="bar">c</record>
</records>
"""

# json style
* assert foo.records.record.length == 3

# xpath assertions
* match foo count(/records//record) == 3
* match foo //record[@index=2] == 'b'
* match foo //record[@foo='bar'] == 'c'

* match foo == <records><record index="1">a</record><record index="#? _ &gt; 1">b</record><record index="3" foo="bar">#string</record></records>

# this xml doc has white-space within it at troublesome places
* def xml = <?xml version="1.0" encoding="UTF-8"?> <response> <result>succeed</result> <records> <record> <browser_port>8008</browser_port> <current_date_time>2017-04-03 20:29:58 CDT</current_date_time> <date_time_server_started>2017-03-21 12:23:55 CDT</date_time_server_started> <os_version>Red Hat Enterprise Linux 6 2.6.32-573.12.1.el6.x86_64, 64 Bit, x86_64</os_version> <product_version>R04M001170316</product_version> <product_database_version>20170131131718</product_database_version> <replication_heartbeat_timestamp>2017-04-03 20:25:00 CDT</replication_heartbeat_timestamp> </record> </records> </response>
* match xml count(/response/records//record) == 1
* match xml/response/result == 'succeed'

Scenario: when xpath exressions return xml chunks (or node lists)

* def response = 
"""
<teachers>
	<teacher department="science" id="309">
		<subject>math</subject>
		<subject>physics</subject>
	</teacher>
	<teacher department="arts" id="310">
		<subject>political education</subject>
		<subject>english</subject>
	</teacher>
</teachers>
"""
* def expected = <teacher department="science" id="309"><subject>math</subject><subject>physics</subject></teacher>
* def teacher = //teacher[@department='science']
* match teacher == expected
* def teacher = get response //teacher[@department='science']
* match teacher == expected
* match //teacher[@department='science'] == expected

* def expected = ['math', 'physics']
* def subjects = //teacher[@department='science']/subject
* match subjects == expected
* def subjects = get response //teacher[@department='science']/subject
* match subjects == expected
* match //teacher[@department='science']/subject == expected
* match //teacher[@department='science']/subject == ['math', 'physics']
* match //teacher[@department='science']/subject contains ['physics', 'math']

* def teachers = response
* def subjects = get teachers //teacher[@department='science']/subject
* match subjects contains ['physics', 'math']
* match teachers //teacher[@department='science']/subject == ['math', 'physics']

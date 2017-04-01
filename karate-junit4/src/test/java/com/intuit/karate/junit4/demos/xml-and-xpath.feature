Feature:

Scenario:

* def foo =
"""
<records>
  <record index="1">a</record>
  <record index="2">b</record>
  <record index="3" foo="bar">c</record>
</records>
"""
* assert foo.records.record.length == 3

* def count = get foo count(/records//record)
* assert count == 3

* def second = get foo //record[@index=2]
* assert second == 'b'

* match foo //record[@foo='bar'] == 'c'

* match foo == <records><record index="1">a</record><record index="#? _ &gt; 1">b</record><record index="3" foo="bar">#string</record></records>

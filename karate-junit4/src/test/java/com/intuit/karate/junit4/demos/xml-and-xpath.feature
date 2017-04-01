Feature:

Scenario:

* def foo =
"""
<records>
  <record>a</record>
  <record>b</record>
  <record>c</record>
</records>
"""
* assert foo.records.record.length == 3
* def count = get foo count(/records//record)
* assert count == 3
* match foo == <records><record>a</record><record>b</record><record>c</record></records>

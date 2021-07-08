Feature:

Scenario: make sure [set] updates variable for xml
* def jsFunction =
"""
  function fn(xml){
    return karate.prettyXml(xml);
  }
"""
* def cat = <cat><name>Billie</name></cat>
* set cat /cat/name = 'Jean'
* xml temp = jsFunction(cat)
* print temp
* match temp / == <cat><name>Jean</name></cat>


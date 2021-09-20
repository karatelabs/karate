Feature:

Scenario:
* def readProps =
"""
function(path) {
  var stream = karate.readAsStream(path);
  var props = new java.util.Properties();
  props.load(stream);
  return props;
}
"""
* def props = readProps('read-properties.properties')
* match props == { hello: 'world' }

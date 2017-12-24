function() {
  var port = karate.properties['karate.server.port'];
  port = port || '8080';
  return { serverUrl: 'http://localhost:' + port }
}

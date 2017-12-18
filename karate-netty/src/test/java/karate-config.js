function() {
  var port = karate.properties['karate.server.port'];
  karate.log('server port detected:', port);
  return { serverUrl: 'http://localhost:' + port }
}

function() {
  var port = karate.properties['karate.server.port'];
  return { demoBaseUrl: 'http://127.0.0.1:' + port };
}

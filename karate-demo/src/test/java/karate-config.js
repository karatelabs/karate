function() {
  var port = karate.properties['karate.server.port'];
  if (!port) port = 8080;
  return { demoBaseUrl: 'http://127.0.0.1:' + port };
}

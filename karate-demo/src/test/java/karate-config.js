function() {
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 5000);
  var port = karate.properties['karate.server.port'];
  if (!port) port = 8080;
  return { demoBaseUrl: 'http://127.0.0.1:' + port };
}

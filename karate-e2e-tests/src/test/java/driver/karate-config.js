function fn() {
  var serverPort = karate.properties['server.port'] || 8080;
  var config = {
    serverUrl: 'http://localhost:' + serverPort
  };
  return config;
}

function fn() {
  karate.configure('driver', { type: 'chrome', showDriverLog: true });
  var serverPort = karate.properties['server.port'] || 8080;
  var config = {
    serverUrl: 'http://localhost:' + serverPort
  };
  return config;
}

function fn() {
  var driverStart = karate.env != 'docker';
  karate.configure('driver', { type: 'chrome', showDriverLog: true, start: driverStart });
  var serverPort = karate.properties['server.port'] || 8080;
  var config = {
    serverUrl: 'http://localhost:' + serverPort
  };
  return config;
}

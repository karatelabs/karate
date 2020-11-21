function fn() {
  karate.configure('driver', { type: 'chrome', showDriverLog: true })
  var config = {
    serverUrl: 'http://localhost:8080'
  };
  return config;
}

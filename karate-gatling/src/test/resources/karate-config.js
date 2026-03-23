function fn() {
  var port = karate.properties['mock.port'] || '8080';
  var config = {
    baseUrl: 'http://localhost:' + port
  };
  return config;
}

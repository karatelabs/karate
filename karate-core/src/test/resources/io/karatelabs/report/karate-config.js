function fn() {
  var port = karate.properties['karate.server.port'] || '8080';
  var config = {
    baseUrl: 'http://localhost:' + port
  };
  karate.log('karate-config: using baseUrl', config.baseUrl);
  return config;
}

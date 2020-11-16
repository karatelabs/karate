function fn() {
  var port = karate.properties['karate.server.port'] || 8080;
  return {
    mockServerUrl: 'http://localhost:' + port + '/v1/'
  }
}

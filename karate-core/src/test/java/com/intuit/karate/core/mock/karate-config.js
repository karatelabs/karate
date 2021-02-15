function fn() {
  var port = karate.properties['karate.server.port'] || 8080;
  var prefix = karate.properties['karate.ssl'] ? 'https' : 'http';
  if (prefix === 'https') {
    karate.configure('ssl', true);
  }
  return {
    mockServerUrl: prefix + '://localhost:' + port + '/v1/'
  }
}

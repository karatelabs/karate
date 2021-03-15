function fn() {
  var port = karate.properties['karate.server.port'];
  port = port || '8080';
  var ssl = karate.properties['karate.server.ssl'];
  if (ssl) {
    karate.log('using ssl:', ssl);
    karate.configure('ssl', true);
  }
  var proxy = karate.properties['karate.server.proxy'];
  if (proxy) {
    karate.log('using proxy:', proxy);
    karate.configure('proxy', proxy);
  }
  return { mockServerUrl: (ssl ? 'https' : 'http') + '://localhost:' + port + '/v1/' }
}

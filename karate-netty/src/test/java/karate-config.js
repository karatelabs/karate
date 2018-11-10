function fn() {
  var port = karate.properties['karate.server.port'];
  port = port || '8080';
  return { mockServerUrl: 'http://localhost:' + port + '/v1/', mockPort: ~~port }
}

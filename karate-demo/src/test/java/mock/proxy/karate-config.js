function fn() { 
  var port = karate.properties['demo.server.port'] || '8080';
  var protocol = 'http';
  if (karate.properties['demo.server.https'] === 'true') {
    protocol = 'https';
    karate.configure('ssl', true);
  }  
  var config = { demoBaseUrl: protocol + '://127.0.0.1:' + port };
  var proxyPort = karate.properties['demo.proxy.port'];
  if (proxyPort) {
    karate.configure('proxy', 'http://127.0.0.1:' + proxyPort);
  }
  return config;
}

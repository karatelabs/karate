function() {
  // karate.configure('connectTimeout', 5000);
  // karate.configure('readTimeout', 5000);  
  var port = karate.properties['demo.server.port'];  
  if (!port) {
    port = karate.env == 'web' ? 8090 : 8080;
  }
  return { demoBaseUrl: 'http://127.0.0.1:' + port };
}

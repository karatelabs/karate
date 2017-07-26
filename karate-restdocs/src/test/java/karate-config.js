function() { 
  var port = karate.properties['demo.server.port'];  
  if (!port) {
    port = 8080;
  }
  karate.configure('httpClientClass', 'com.intuit.karate.restdocs.RestDocsHttpClient');
  return { demoBaseUrl: 'http://127.0.0.1:' + port };
}

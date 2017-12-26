function() { 
  var port = karate.properties['demo.server.port'];  
  if (!port) {
    port = 8080;
  }
  karate.configure('httpClientClass', 'com.intuit.karate.restdocs.RestDocsHttpClient');
  var config = { demoBaseUrl: 'http://127.0.0.1:' + port };
  var authInfo = karate.callSingle('classpath:auth-single.js', config);
  config.authInfo = authInfo;
  return config;
}

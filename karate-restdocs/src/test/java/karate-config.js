function() { 
  var port = karate.properties['demo.server.port'];  
  if (!port) {
    port = 8080;
  }
  karate.configure('httpClientClass', 'com.intuit.karate.restdocs.RestDocsHttpClient');
  var config = { demoBaseUrl: 'http://127.0.0.1:' + port };
  var result = karate.callSingle('classpath:demo/headers/common-noheaders.feature', config);
  config.authInfo = { authTime: result.time, authToken: result.token };
  return config;
}

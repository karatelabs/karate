function() {
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 5000);  
  var port = karate.properties['demo.server.port'];  
  if (!port) {
    port = karate.env == 'web' ? 8090 : 8080;
  }
  var config = { demoBaseUrl: 'http://127.0.0.1:' + port };
  // 'callSingle' is guaranteed to run only once even across all threads
  var authInfo = karate.callSingle('classpath:auth-single.js', config);
  config.authInfo = authInfo;
  return config;
}

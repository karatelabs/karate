function() {
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 5000);  
  var port = karate.properties['demo.server.port'];  
  if (!port) {
    port = karate.env == 'web' ? 8090 : 8080;
  }
  var config = { demoBaseUrl: 'http://127.0.0.1:' + port };
  if (karate.env == 'proxy') {
    var proxyPort = karate.properties['demo.proxy.port']
    karate.configure('proxy', 'http://127.0.0.1:' + proxyPort);
  }
  if (karate.env != 'mock' && karate.env != 'proxy') {
    // 'callSingle' is guaranteed to run only once even across all threads
    var result = karate.callSingle('classpath:demo/headers/common-noheaders.feature', config);
    // and it sets a variable called 'authInfo' used in headers-single.feature
    config.authInfo = { authTime: result.time, authToken: result.token };
  }
  return config;
}

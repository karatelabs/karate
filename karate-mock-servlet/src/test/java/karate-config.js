function fn() {
  var config = {
    demoBaseUrl: 'http://localhost:8080'
  };
  if (karate.env == 'dev-mock-springmvc') {
    var result = karate.callSingle('classpath:demo/headers/common-noheaders.feature', config);
    config.authInfo = { authTime: result.time, authToken: result.token };
  }
  return config;
}

function(config) {
  var result = karate.call('classpath:demo/headers/common-noheaders.feature', config);
  return { authTime: result.time, authToken: result.token };
}

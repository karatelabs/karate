function fn() {
  var config = {
    functionFromKarateConfig: function(){ return 'resultFromFunctionFromKarateConfig'; },
    serverUrl: 'http://localhost:' + karate.properties['server.port']
  };  
  var result = karate.callSingle('call-single-from-config.feature', config);
  config.message = result.response.message;
  var result2 = karate.callSingle('call-single-from-config2.feature', result);
  config.message2 = result2.message;  
  return config;
}

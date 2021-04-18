function fn() {
  var config = {
    functionFromKarateConfig: function(){ return 'resultFromFunctionFromKarateConfig'; },
    serverUrl: 'http://localhost:' + karate.properties['server.port']
  };  
  var result = karate.callSingle('call-single-from-config.feature', config);
  config.message = result.response.message;
  // this used to throw the [Multi threaded access requested by thread xxx but is not allowed for language(s) js.] error
  config.Hello = result.Hello;
  var result2 = karate.callSingle('call-single-from-config2.feature', result);
  config.message2 = result2.message;  
  return config;
}

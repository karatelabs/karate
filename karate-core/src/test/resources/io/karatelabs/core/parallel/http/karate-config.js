function fn() {
  var config = {
    serverUrl: 'http://localhost:' + karate.properties['server.port'],
    message: 'from config',
    // Function from config - tests cloning
    sayHello: function(x) { return 'hello ' + x; }
  };

  // callSingle with HTTP call and Java interop - tests config snapshot cloning
  var result = karate.callSingle('classpath:io/karatelabs/core/parallel/http/http-callsingle-config.feature', config);
  config.HelloConfigSingle = result.HelloSingle;

  // TODO: Java Function as callable - V1 parity pending
  // V1 uses Hello.sayHelloFactory() which returns Function<String, String>
  // and it's callable in JS like: sayHello('world')
  // This requires JS engine to treat Java Function, Callable, Runnable, Predicate as JsCallable
  // var result3 = karate.callSingle('http-callsingle-factory.js');
  // config.sayHelloFactory = result3.sayHello;

  return config;
}

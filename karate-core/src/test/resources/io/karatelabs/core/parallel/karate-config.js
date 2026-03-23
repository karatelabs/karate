function fn() {
  var config = {
    // Function that will be shared across all scenarios
    sharedFunction: function(x) { return 'hello ' + x; },
    // Mutable object to test isolation
    configData: { count: 0, name: 'fromConfig' }
  };

  // callSingle with classpath prefix (tests the fix for double-resolution)
  var singleResult = karate.callSingle('classpath:io/karatelabs/core/parallel/call-single-data.js');
  config.singleData = singleResult;
  config.getSingleId = singleResult.getId;

  return config;
}

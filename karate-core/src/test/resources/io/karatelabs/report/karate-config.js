function fn() {
  var port = karate.properties['karate.server.port'] || '8080';
  var config = {
    baseUrl: 'http://localhost:' + port
  };
  karate.log('karate-config: using baseUrl', config.baseUrl);
  // Tag-guarded beforeScenario so the dev-preview report shows a beforeScenario
  // hook with a nested call. A feature-level `configure beforeScenario` fires
  // AFTER the hook already ran for the current scenario, so karate-config.js is
  // the only place to install a beforeScenario for a specific scenario.
  var tags = karate.tags || [];
  if (tags.indexOf('hook-before-call') >= 0) {
    karate.configure('beforeScenario', function() {
      karate.log('beforeScenario: running helper');
      karate.call('classpath:io/karatelabs/report/hooks-helper.feature');
    });
  }
  return config;
}

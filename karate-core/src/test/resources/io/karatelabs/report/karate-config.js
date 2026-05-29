function fn() {
  var port = karate.properties['karate.server.port'] || '8080';
  var config = {
    baseUrl: 'http://localhost:' + port
  };
  karate.log('karate-config: using baseUrl', config.baseUrl);
  // Demonstrate karate.callSingle() from karate-config.js — runs the helper
  // exactly once per suite under a lock. The winning
  // scenario's first step (or its beforeScenario synthetic step, if one is
  // present) carries the helper's nested FeatureResult so its HTTP traffic
  // renders in the HTML report.
  var auth = karate.callSingle('classpath:io/karatelabs/report/callsingle-auth.feature', { baseUrl: config.baseUrl });
  config.authToken = auth.authToken;
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

function() {   
  var env = karate.env; // get system property 'karate.env'
  if (!env) {
    env = 'dev';
  }
  var config = {
    env: env,
    testConfig: 'bar',
    wiremockPort: karate.properties['wiremock.port']
  }
  if (env == 'dev') {
    // customize
    // e.g. config.foo = 'bar';
  } else if (env == 'e2e') {
    // customize
  }
  config.myObject = karate.read('classpath:test.json');
  return config;
}
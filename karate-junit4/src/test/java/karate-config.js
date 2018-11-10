function fn() {   
  var env = karate.env; // get system property 'karate.env'
  if (!env) {
    env = 'dev';
  }
  var config = {
    env: env,
    testConfig: 'bar'
  }
  if (env == 'dev') {
    // customize
    // e.g. config.foo = 'bar';
  } else if (env == 'e2e') {
    // customize
  }
  config.myObject = karate.read('classpath:test.json');
  config.myFunction = karate.read('classpath:test.js');
  return config;
}
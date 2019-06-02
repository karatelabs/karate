function fn() {
  var config = { envoverride: 'done' };
  config.greeter = Java.type('com.intuit.karate.junit4.config.Greeter').INSTANCE;
  return config;
}

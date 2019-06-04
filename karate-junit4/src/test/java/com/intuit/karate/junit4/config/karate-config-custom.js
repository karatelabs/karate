function fn() {
  var config = { envoverride: 'done' };
  config.greeter = Java.type('com.intuit.karate.junit4.config.Greeter').INSTANCE;
  config.child = function(params) { return karate.call('classpath:child.feature', params) }
  return config;
}

function fn() {
  var config = { envoverride: 'done' };
  config.greeter = Java.type('com.intuit.karate.junit4.config.Greeter').INSTANCE;
  config.utils = karate.call('utils.feature');
  config.child = function(params) { return karate.call('classpath:child.feature', params) };
  var temp = {
    sslConfig: true,
    apiUrl: 'https://my-api.com',
    api2Url: 'https://my-api2.com',
    proxy: { uri: 'http://my-proxy.com:3128', nonProxyHosts: [ 'my-api2.com' ]}
  };
  karate.configure('proxy', temp.proxy); // regression test for configure proxy
  return config;
}

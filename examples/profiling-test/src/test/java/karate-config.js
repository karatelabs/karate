function fn() {
  var config = karate.call('classpath:perf/called.feature');
  karate.log('config:', config);
  return config;
}
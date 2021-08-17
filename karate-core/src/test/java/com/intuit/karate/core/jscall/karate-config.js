function fn() {
  var config = {};
  config.utils = karate.call('classpath:com/intuit/karate/core/jscall/utils.feature');
  return config;
}
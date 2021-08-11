function fn() {
  var config = {};
  config.utils = karate.callSingle('classpath:com/intuit/karate/core/jscall/utils.feature');
  return config;
}
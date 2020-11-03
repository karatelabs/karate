function fn() {
  var config = {
    configSource: 'custom',
    configUtilsJs: {
      someText: 'hello world',
      someFun: function () {
        return 'hello world'
      }
    },
    configUtils: karate.call('classpath:com/intuit/karate/runtime/karate-config-utils.feature')
  };
  return config;
}

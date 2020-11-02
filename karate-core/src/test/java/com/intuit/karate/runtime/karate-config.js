function fn() {
  var config = {
    configSource: 'custom',
    configUtilsJs: {
      someText: 'hello world',
      someFun: function () {
        return 'hello world'
      }
    },
    utils: karate.call('classpath:com/intuit/karate/runtime/utils.feature')
  };
  return config;
}

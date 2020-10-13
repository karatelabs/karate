function fn() {
  var config = {
    configSource: 'custom',
    utils: {
      someText: 'hello world',
      someFun: function(){ return 'hello world' }
    }
  };
  return config;
}

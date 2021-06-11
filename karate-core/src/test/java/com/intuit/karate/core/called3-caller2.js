function fn() {
  var result = karate.call('called3.js');
  karate.log('varA:', result.varA);
  return result.varA;
}

function() {
  var result = karate.callSingle('called3.js');
  karate.log('varA:', result.varA);
  return result.varA;
}

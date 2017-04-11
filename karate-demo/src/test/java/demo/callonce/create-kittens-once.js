function(kittens) {
  var CatsHolder = Java.type('demo.callonce.CatsHolder');
  var result = CatsHolder.cats;
  if (result) {
    karate.log('*** returning cached result ! ***');
    return result;
  }
  result = karate.call('../calltable/kitten-create.feature', kittens);
  CatsHolder.cats = result;
  return result;
}

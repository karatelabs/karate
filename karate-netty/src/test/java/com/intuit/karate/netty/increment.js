function(varName) {
  return function() {
    var curVal = karate.get(varName);
    var nextVal = curVal + 1;
    karate.set(varName, nextVal);
    return ~~nextVal;
  }
}  

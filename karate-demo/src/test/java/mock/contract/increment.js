function fn() {
  var varName = '_curId';
  karate.set(varName, 0);
  return function() {
    var curId = karate.get(varName);
    var nextId = curId + 1;
    karate.set(varName, nextId);
    return ~~nextId;
  }
}  

function fn() {
  karate.set('_curId', 0);
  return function() {
    var curId = karate.get('_curId');
    var nextId = curId + 1;
    karate.set('_curId', nextId);
    return ~~nextId;
  }
}  

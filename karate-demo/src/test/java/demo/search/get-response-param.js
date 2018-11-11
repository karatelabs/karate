function fn(name) {
  var response = karate.get('response');
  var list = response[name];
  if (!list) {
    return null;
  }
  return list[0];
}

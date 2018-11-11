function fn(array) {
  var ArrayList = Java.type('java.util.ArrayList')
  var Collections = Java.type('java.util.Collections')
  var list = new ArrayList();
  for (var i = 0; i < actual.length; i++) {
    list.add(actual[i]);
  }
  Collections.sort(list, java.lang.String.CASE_INSENSITIVE_ORDER)
  return list;
}

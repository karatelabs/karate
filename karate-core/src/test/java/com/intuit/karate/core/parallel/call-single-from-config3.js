function fn() {
  var result = {};
  var Hello = Java.type('com.intuit.karate.core.parallel.Hello');
  result.sayHello = Hello.sayHello;
  return result;
}

function fn() {
  var result = {};
  var Hello = Java.type('com.intuit.karate.core.parallel.Hello');
  // this is the recommended way to create a java function reference
  // that can be re-used within karate JS blocks
  result.sayHello = Hello.sayHelloFactory();
  return result;
}

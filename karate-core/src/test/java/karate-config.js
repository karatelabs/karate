function fn() {
  console.log("HEllo");
  var env = karate.env; // get system property 'karate.env'
  var config = {
    env: env,
    secret: 'secretvalueshouldnotcomeincucumberreport',
    baseURL: 'https://jsonplaceholder.typicode.com',
    number: '2'
  }
  var results = karate.callSingle('classpath:com/intuit/karate/report/suite.feature@test1',config);
  results = karate.callSingle('classpath:com/intuit/karate/report/suite2.feature@test2',config);
  return {

    configSource: 'normal' }
}
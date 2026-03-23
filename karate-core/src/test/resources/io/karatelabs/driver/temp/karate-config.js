function fn() {
  var serverUrl = karate.properties['karate.serverUrl'] || 'http://localhost:18899';
  karate.log('Using serverUrl:', serverUrl);

  // Check if headless mode is overridden (for manual testing with visible browser)
  var headless = karate.properties['karate.driver.headless'];
  headless = (headless === 'false') ? false : true;

  // Configure driver for local Chrome
  karate.configure('driver', {
    type: 'chrome',
    headless: headless,
    userDataDir: 'target/chrome-temp-test'
  });

  return {
    serverUrl: serverUrl
  };
}

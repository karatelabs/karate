Feature: axe accessibility native

  Background:
    * configure driver = { type: 'chrome', showDriverLog: true }

  Scenario:
    * def axeJs =
      """
      function(){
      var url = new java.net.URL('https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.0.2/axe.min.js');
      var bfReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(url.openStream()));

      var response = "";
      var inputLine;

      while ((inputLine = bfReader.readLine()) != null){
        response = response + inputLine;
      }

      bfReader.close();
      return response;
      }
      """
    * driver 'https://www.seleniumeasy.com/test/dynamic-data-loading-demo.html'
    # inject axe script
    * driver.script(axeJs());
    # execute axe
    * def axeResponse = driver.scriptAwaitPromise('axe.run().then(results => JSON.stringify(results))');
    # do as you please with the response
    * print axeResponse.violations
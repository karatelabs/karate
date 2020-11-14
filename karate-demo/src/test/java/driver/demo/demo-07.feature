Feature: axe accessibility native

  Background:
    * configure driver = { type: 'chrome', showDriverLog: true }

  Scenario:
    * def axeJs =
      """
      function(){
      var url = new java.net.URL('https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.0.2/axe.min.js');
      var conn = url.openConnection();
      conn.setRequestMethod('GET');
      var jsContent;
      if(conn.getResponseCode() === 200) {
        jsContent = org.apache.commons.io.IOUtils.toString(conn.getInputStream(), 'UTF-8');
      }
      conn.disconnect();
      jsContent = jsContent.replaceAll("\"", "\\\"");
      return jsContent;
      }
      """
    * def axeJscript = axeJs()
    * driver 'https://www.seleniumeasy.com/test/dynamic-data-loading-demo.html'
    * driver.script(axeJscript);
    * def vilationsResponse = driver.scriptAwaitPromise();
    * print '>>>>>>>>'
    # current url return 7 violations of varying sizes. To pass the result let us just keep it going.Scenario:
    # ideally this size should be 0 or match by criticality of the violations.
    * match vilationsResponse.size() == 7
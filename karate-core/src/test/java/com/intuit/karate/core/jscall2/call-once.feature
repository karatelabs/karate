#@ignore
Feature:: common setup

  Scenario: common setup

    * def isResponseStatus200_callOnce =
    """
    function() {
      if( responseStatus != 200){
        karate.log("Retry since expectedStatus 200 != actual responseStatus: " + responseStatus);
        return false;
      }
      return true;
    }
    """
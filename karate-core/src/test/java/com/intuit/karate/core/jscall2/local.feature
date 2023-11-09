#This example is in its own file since it prevents all tests from reading responseStatus
Feature: responseStatus callOnce test

  Background:

    * def isResponseStatus200 =
    """
    function() {
      if( responseStatus != 200){
        karate.log("Retry since expectedStatus 200 != actual responseStatus: " + responseStatus);
        return false;
      }
      return true;
    }
    """

    # if comment out callOnce the local js test will pass

    * callonce read('classpath:com/intuit/karate/core/jscall2/call-once.feature')




  #########################
  #####    failing    #####
  #########################

  Scenario: callOnce test

    * url serverUrl
    * method get
    * status 200

    * print 'responseStatus: ' + responseStatus
    * assert isResponseStatus200_callOnce()


  #fails from callOnce
  Scenario: local js test

    * url serverUrl
    * method get
    * status 200

    * print 'responseStatus: ' + responseStatus
    * assert isResponseStatus200()
Feature: xml samples and tests

Scenario: xpath with namespaces

* def foo =
"""
<acc:getAccountByPhoneNumber>
   <acc:phoneNumber>help me</acc:phoneNumber>
   <!--Optional:-->
   <acc:phoneNumberSearchOption>
      <!--Optional:-->
      <acc:searchWirelessInd>true</acc:searchWirelessInd>
      <!--Optional:-->
      <acc:searchVoipInd>false</acc:searchVoipInd>
      <!--Optional:-->
      <acc:searchTollFreeInd>false</acc:searchTollFreeInd>
   </acc:phoneNumberSearchOption>
</acc:getAccountByPhoneNumber>
"""

* def phoneNumber = get foo /getAccountByPhoneNumber/phoneNumber
* match phoneNumber == 'help me'
* set foo /getAccountByPhoneNumber/phoneNumber = '4160618039'
* xmlstring bar = foo
* match bar contains '<acc:phoneNumber>4160618039</acc:phoneNumber>'
@ignore
Feature: scratch pad to work on only one construct at a time

Scenario: test
* configure ssl = true
* url 'https://pprdpspas80k.ie.intuit.net/DataAdapter/rest/ratable/v1/licenseInfo'
* header intuit-tid = 'foo'
* request 
"""
{
  "QBLicense": "6206-4827-4850-254",
  "SKU": "Pro",
  "Source": "install_full",
  "MajorVersion": "25",
  "MinorVersion": "07"
 }
"""
* method post
* status 200

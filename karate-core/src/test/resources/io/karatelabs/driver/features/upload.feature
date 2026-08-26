Feature: File Upload
  inputFile() is the ONE way to drive a file input — the browser forbids setting
  its value from script. The status divs echo "<name>:<content>", read back via
  the File API, so a pass proves the browser actually read the file off its
  filesystem (the fixture is copied into the browser container by the test setup).
  File refs resolve like read(): a bare path is relative to this feature's dir.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/upload'

  Scenario: Single file via driver.inputFile
    * driver.inputFile('#file-upload', 'upload-sample.txt')
    * waitForText('#single-status', 'upload-sample.txt:hello-upload')

  Scenario: Bare keyword with an array of files
    * inputFile('#file-multi', ['upload-sample.txt', 'upload-extra.txt'])
    * waitForText('#multi-status', '2 files')
    * def status = text('#multi-status')
    * match status contains 'upload-sample.txt:hello-upload'
    * match status contains 'upload-extra.txt:second-file'

  Scenario: Hidden file input behind a styled button
    * inputFile('#file-hidden', 'upload-sample.txt')
    * waitForText('#hidden-status', 'upload-sample.txt:hello-upload')

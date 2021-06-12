Feature: upload file

Scenario: use defaults for file-name and unknown file-extension (content-type)
Given url mockServerUrl
And path 'upload', 'excel'
And multipart file myFile = { read: 'test.xlsx' }
When method post
Then status 200
And match response == {charset: 'UTF-8', filename: 'test.xlsx', transferEncoding: 'binary', name: 'myFile', contentType: 'application/octet-stream', value: '#ignore' }

Scenario: user-specified file-name and content-type
Given url mockServerUrl
And path 'upload', 'excel'
And multipart file myFile = { read: 'test.xlsx', filename: 'my-file.xlsx', contentType: 'text/csv' }
When method post
Then status 200
And match response == {charset: 'UTF-8', filename: 'my-file.xlsx', transferEncoding: 'binary', name: 'myFile', contentType: 'text/csv', value: '#ignore' }

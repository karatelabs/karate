Feature: report embed

Scenario: embed html
    * karate.embed('<h1>Hello World</h1>', 'text/html')

@apache
@mock-servlet-todo
Scenario: embed images
    * def bytes1 = read('../upload/karate-logo.jpg')
    * karate.embed(bytes1, 'image/jpeg')
    * def bytes2 = read('file:src/test/resources/karate-hello-world.jpg')
    * karate.embed(bytes2, 'image/jpeg')

Scenario: embed pdf
    # since the cucumber html reporting plugin does not support rendering PDF-s inline
    # this is an example of how to create a custom HTML file that can show the PDF
    # it does involve an extra click but there are limitations on loading PDF-s into IFRAME-s
    * def bytes = read('../upload/test.pdf')
    * def embedder = read('embed-pdf.js')
    * embedder(bytes)

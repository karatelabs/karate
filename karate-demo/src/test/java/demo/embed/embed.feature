Feature: report embed

Scenario: embed html
    * eval karate.embed('<h1>Hello World</h1>', 'text/html')

Scenario: embed image
    * def bytes = read('../upload/karate-logo.jpg')
    * eval karate.embed(bytes, 'image/jpeg')

Scenario: embed pdf
    # since the cucumber html reporting plugin does not support rendering PDF-s inline
    # this is an example of how to create a custom HTML file that can show the PDF
    # it does involve an extra click but there are limitations on loading PDF-s into IFRAME-s
    * def bytes = read('../upload/test.pdf')
    * def embedder = read('embed-pdf.js')
    * eval embedder(bytes)

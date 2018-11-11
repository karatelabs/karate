function fn(o) {
    var time = java.lang.System.currentTimeMillis();
    var pdfPath = time + '.pdf';
    karate.write(o, pdfPath);
    karate.log('saved pdf to:', pdfPath);
    var html = '<script src="https://cdnjs.cloudflare.com/ajax/libs/pdfobject/2.1.0/pdfobject.min.js"></script>'
        + '\n<div id="divPdf"></div>'
        + '\n<script>PDFObject.embed("' + pdfPath + '", "#divPdf");</script>';
    var htmlPath = time + '.html';
    karate.write(html, htmlPath);
    karate.log('saved html to:', htmlPath);
    karate.embed('<a href="../' + htmlPath + '" target="_blank">(right-click and open in new tab)</a>', 'text/html');
}

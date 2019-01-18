function fn() {
    var port = karate.properties['mock.port'];
    if (!port) port = 8080;
    return { baseUrl: 'http://localhost:' + port + '/cats' };
}
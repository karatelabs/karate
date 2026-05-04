// Echo a cookie back from request.cookies. Used to test the JS binding
// of HttpRequest.getCookies() — see DemoAppTest.testRequestCookies.

var name = request.param('name') || 'session'
var c = request.cookies[name]

if (c) {
    response.body = { name: c.name, value: c.value }
} else {
    response.body = { found: false }
}

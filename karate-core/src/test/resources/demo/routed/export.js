// jsRoute fixture — a JS handler living OUTSIDE the apiPrefix, reached via
// config.jsRoute(). The original request path is preserved, so pathMatches()
// works against the route pattern.
var suffix = '';
if (request.pathMatches('/files/{id}/data')) {
    suffix = ':' + request.pathParams.id;
}
// body FIRST — assigning a string body stamps Content-Type: text/plain,
// which would overwrite a header set before it
response.body = 'export-ok' + suffix;
response.header('Content-Type', 'text/csv');
response.header('Content-Disposition', 'attachment; filename="export.csv"');

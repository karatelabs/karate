// Generic CRUD mock - evaluated as a block for each request
// Available: request, response, session

session.items = session.items || {};
session.counter = session.counter || 1;

// Route based on path pattern
if (request.pathMatches('/items/{id}')) {
    var id = request.pathParams.id;
    if (request.get) {
        var item = session.items[id];
        if (item) {
            response.body = item;
        } else {
            response.status = 404;
            response.body = { error: 'Not found', id: id };
        }
    } else if (request.put) {
        session.items[id] = request.body;
        session.items[id].id = id;
        response.body = session.items[id];
    } else if (request.delete) {
        delete session.items[id];
        response.status = 204;
    }
} else if (request.pathMatches('/items')) {
    if (request.get) {
        response.body = Object.values(session.items);
    } else if (request.post) {
        var id = '' + session.counter++;
        var item = request.body;
        item.id = id;
        session.items[id] = item;
        response.status = 201;
        response.body = item;
    }
} else {
    response.status = 404;
    response.body = { error: 'Unknown path' };
}

session.payments = session.payments || {};
session.counter = session.counter || 1;
if (request.pathMatches('/payments/{id}')) {
  let id = request.pathParams.id;
  if (request.put) {
    var payment = request.body;
    session.payments[id] = payment;
    response.body = payment;
  } else if (request.delete) {
    delete session.payments[id]
  } else { // get
    response.body = session.payments[id];
    if (!response.body) {
      response.status = 404;
    }
  }
} else if (request.post) {
  var payment = request.body;
  let id = '' + session.counter++;
  payment.id = id;
  session.payments[id] = payment;
  response.body = payment;
} else { // get all
  response.body = Object.values(session.payments);
}

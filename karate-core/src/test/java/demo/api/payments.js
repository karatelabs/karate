session.payments = session.payments || {};
session.counter = session.counter || 1;
var id = ~~request.pathParam;
if (id) {
  if (request.put) {
    var payment = request.body;
    session.payments[id] = payment;
    response.body = payment;
  } else if (request.delete) {
    session.payments = context.remove(session.payments, id);
  } else { // get
    response.body = session.payments[id];
  }
} else if (request.post) {
  var payment = request.body;
  id = session.counter++;
  payment.id = id;
  session.payments[id] = payment;
  response.body = payment;
} else { // get all
  response.body = Object.values(session.payments);
}

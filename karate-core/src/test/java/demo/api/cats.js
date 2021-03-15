session.cats = session.cats || [];
if (request.post) {
  var cat = request.body;
  cat.id = session.cats.length + 1;
  session.cats.push(cat);
  response.body = cat;
} else if (request.pathParam) {
  var id = ~~request.pathParam;
  if (request.get) {
    var cat = session.cats.find(c => c.id === id);
    response.body = cat;
  }
} else { // get all
  response.body = session.cats;
}

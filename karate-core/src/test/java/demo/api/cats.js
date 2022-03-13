session.cats = session.cats || [];
if (request.post) {
  let cat = request.body;
  cat.id = session.cats.length + 1;
  session.cats.push(cat);
  response.body = cat;
} else if (request.pathMatches('/{resource}/{id}')) {
  let id = ~~request.pathParams.id;
  if (request.get) {
    let cat = session.cats.find(c => c.id === id);
    response.body = cat;
  }
} else { // get all
  response.body = session.cats;
}

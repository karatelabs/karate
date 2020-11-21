var karate = {};
karate.get = function(id) { return document.getElementById(id) };
karate.setHtml = function(id, value) { this.get(id).innerHTML = value };
karate.addHtml = function(id, value) { var e = this.get(id); e.innerHTML = e.innerHTML + value };

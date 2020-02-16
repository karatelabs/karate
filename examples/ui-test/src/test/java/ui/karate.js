var karate = {};
karate.get = function(id) { return document.getElementById(id) };
karate.setHtml = function(id, value) { this.get(id).innerHTML = value };

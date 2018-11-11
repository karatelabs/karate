function fn(arr, cat){  
  var res = []; 
  for (var i = 0; i < arr.length; i++) {
    var res1 = karate.call('create-' + arr[i] + '.feature', cat);
    var res2 = karate.call('result.feature', { id: res1.id });
    res.push(res2.response);
  }
  return res; 
}

'use strict';

var unpack = function (array) {
  var findNbSeries = function (array) {
    var currentPlotPack;
    var length = array.length;

    for (var i = 0; i < length; i++) {
      currentPlotPack = array[i][1];
      if(currentPlotPack !== null) {
        return currentPlotPack.length;
      }
    }
    return 0;
  };

  var i, j;
  var nbPlots = array.length;
  var nbSeries = findNbSeries(array);

  // Prepare unpacked array
  var unpackedArray = new Array(nbSeries);

  for (i = 0; i < nbSeries; i++) {
    unpackedArray[i] = new Array(nbPlots);
  }

  // Unpack the array
  for (i = 0; i < nbPlots; i++) {
    var timestamp = array[i][0];
    var values = array[i][1];
    for (j = 0; j < nbSeries; j++) {
      unpackedArray[j][i] = [timestamp * 1000, values === null ? null : values[j]];
    }
  }

  return unpackedArray;
};

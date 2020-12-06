var kjs = {};
kjs.edit = function (event, element) {
  switch (event.code) {
    case 'Escape':
      element.value = '';
      element.dispatchEvent(new Event('keyup-escape'));
      break;
    case 'Enter':
      element.dispatchEvent(new Event('keyup-enter'));
      break;
  }
};
kjs.focus = function (sel) {
  var e = document.querySelector(sel);  
  try {
    e.focus();
    e.selectionStart = e.selectionEnd = e.value.length;
  } catch (x) {
    // ignore
  }
};

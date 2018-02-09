window.onload = function() {
  possibleModes = ['failed', 'skipped', 'passed'];
  tests = {};
  modeBorder = {};
  for (mode of possibleModes) {
    tests[mode] = [];
    modeBorder[mode] = document.createElement('div');
    modeBorder[mode].classList.add(mode+'_border');
  }
  sidenavDiv = document.createElement('div');
  sidenavDiv.classList.add('sidenav');
  sidenavDiv.innerHTML += '<h2>Test Suite Navigation</h2>';
  document.body.appendChild(sidenavDiv);
  scenarios = document.getElementsByClassName('step-cell');
  for (i = 0; i < scenarios.length; ++i) {
    scenarios[i].id = getIdForTest(i);
    scenarios[i].innerHTML = (getTextForTest(i) + ' : ' + scenarios[i].innerHTML);
    mode = scenarios[i].classList[1];
    tests[mode].push(i);
  }
  for (mode of possibleModes) {
    buildSidebarVerboseReport(mode);
    buildSidebarAnchors(mode);
    sidenavDiv.appendChild(modeBorder[mode]);
  }
  console.log(tests);
  console.log(modeBorder);
  function getIdForTest(i) {
    return('test_'+(i+1));
  }
  function getTextForTest(i) {
    return('Test '+(i+1));
  }
  function buildSidebarVerboseReport(mode) {
    verboseReport = document.createElement('p');
    verboseReport.appendChild(document.createTextNode('# of ' + mode + ' tests: ' + tests[mode].length + '/' + scenarios.length));
    verboseReport.appendChild(document.createElement('br'));
    verboseReport.appendChild(document.createTextNode('('+(tests[mode].length/scenarios.length)*100 + '%)'));
    verboseReport.classList.add(mode+'_font');
    modeBorder[mode].appendChild(verboseReport);
  }
  function buildSidebarAnchors(mode) {
    suchTests = tests[mode];
    for (i=0; i<suchTests.length; ++i) {
      anchor = document.createElement('a');
      anchor.setAttribute('href', '#'+getIdForTest(suchTests[i]));
      anchor.appendChild(document.createTextNode(suchTests[i]+1));
      anchor.classList.add('panel');
      anchor.classList.add(mode);
      modeBorder[mode].appendChild(anchor);
      if ((i+1)%6==0) {
        modeBorder[mode].appendChild(document.createElement('br'));
      }
    }
    modeBorder[mode].appendChild(document.createElement('br'));
  }
}

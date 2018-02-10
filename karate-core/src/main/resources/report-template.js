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
  document.body.appendChild(sidenavDiv);
  addKarateLogoToSidebar();
  sidenavDiv.innerHTML += '<h2>Test Suite Navigation</h2>';
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
  buildAccordians();

  function addKarateLogoToSidebar() {
    imgContainer = document.createElement('div');
    sidenavDiv.appendChild(imgContainer);
    imgContainer.classList.add('svgHolder');
    img = document.getElementsByTagName('svg');
    imgContainer.appendChild(img[0]);
    box = img[0].getBBox();
    viewBox = [box.x, box.y, box.width, box.height].join(" ");
    img[0].setAttribute("viewBox", viewBox);
    img[0].style.width = "100px";
    img[0].style.height = "100px";
  }
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
    verboseReport.appendChild(document.createTextNode('('+((tests[mode].length/scenarios.length)*100).toFixed(2) + '%)'));
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
  function buildAccordians(){
    tables = document.getElementsByTagName('table');
    preformatted = document.getElementsByClassName('preformatted');
    buildAccordianForListOfItems(tables);
    buildAccordianForListOfItems(preformatted);
    accordion = document.getElementsByClassName("accordion");
    for (i = 0; i < accordion.length; i++) {
        accordion[i].addEventListener("click", function() {
            this.classList.toggle("active");
            var panel = this.nextElementSibling;
            if (panel.style.display === "block") {
                panel.style.display = "none";
            } else {
                panel.style.display = "block";
            }
        });
    }   
    function buildAccordianForListOfItems(items) {
      for(i=0;i<items.length;++i){
        items[i].classList.add('accordion_panel');
        step = items[i].previousElementSibling;
        step.classList.add('accordion');
      }      
    }
  }
}

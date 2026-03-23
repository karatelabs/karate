function parentId(name) {
  return "parent-" + name;
}

function isEllipsed(name) {
  const child = document.getElementById(name);
  const parent = document.getElementById(parentId(name));
  const emptyData = parent.getAttribute("data-content") === "";
  const hasOverflow = child.clientWidth < child.scrollWidth;

  if (hasOverflow) {
    if (emptyData) {
      parent.setAttribute("data-content", name);
    }
  } else {
    if (!emptyData) {
      parent.setAttribute("data-content", "");
    }
  }
}

function ellipsedLabel ({ name, parentClass = "", childClass = "" }) {
  const child = "<span onmouseover='isEllipsed(\"" + name + "\")' id='" + name + "' class='ellipsed-name " + childClass + "'>" + name + "</span>";

  return "<span class='" + parentClass + "' id='" + parentId(name) + "' data-toggle='popover' data-placement='right' data-container='body' data-content=''>" + child + "</span>";
}

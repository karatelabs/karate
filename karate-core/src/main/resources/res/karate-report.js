window.onload = function () {
  $('div.step-container').each(function (i) {
    var id = this.id;
    var children = $("[data-parent='" + id + "']");
    if (children.length > 0) {
      children.hide();
      $(this).wrap("<a href='javascript:void(0)'></a>").click(function () {
        children.toggle();
      });
    }
  });
  $("table.features-table").tablesorter();
  $("table.tags-table").tablesorter();
}

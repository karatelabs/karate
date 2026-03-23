function getItemLink(item){
	return item.pathFormatted + '.html';
}

function setDetailsLinkUrl(){
    $.each(stats.contents, function (name, data) {
        $('#details_link').attr('href', getItemLink(data));
        return false;
    });
}

function menuItem(item, level, parent, group) {
    if (parent) {
      var dataParent = ' data-parent="' + ((level == 0) ? 'ROOT' : ("menu-" + parent)) + '"';
    } else {
      var dataParent = '';
    }

    if (group)
        var expandButton = '<span id="menu-' + item.pathFormatted + '" style="margin-left: ' + (level * 10) + 'px;" class="expand-button">&nbsp;</span>';
    else
        var expandButton = '<span id="menu-' + item.pathFormatted + '" style="margin-left: ' + (level * 10) + 'px;" class="expand-button hidden">&nbsp;</span>';

    return '<li' + dataParent + '>' + expandButton + '<a href="' + getItemLink(item) + '" class="item withTooltip">' + ellipsedLabel({ name: item.name, parentClass: "nav-tooltip", childClass: "nav-label" }) + '</a></li>';
}

function menuItemsForGroup(group, level, parent) {
    var items = '';

    if (level > 0)
        items += menuItem(group, level - 1, parent, true);

    $.each(group.contents, function (contentName, content) {
        if (content.type == 'GROUP')
            items += menuItemsForGroup(content, level + 1, group.pathFormatted);
        else if (content.type == 'REQUEST')
            items += menuItem(content, level, group.pathFormatted);
    });

    return items;
}

function setDetailsMenu(){
    $('.nav ul').append(menuItemsForGroup(stats, 0));
    $('.nav').expandable();
    $('.nav-tooltip').popover({trigger:'hover'});
}

function setGlobalMenu(){
    $('.nav ul')
      .append('<li><a class="item" href="#ranges"><span class="nav-label">Ranges</span></a></li>')
      .append('<li><a class="item" href="#stats"><span class="nav-label">Stats</span></a></li>')
      .append('<li><a class="item" href="#active_users"><span class="nav-label">Active Users</span></a></li>')
      .append('<li><a class="item" href="#requests"><span class="nav-label">Requests / sec</span></a></li>')
      .append('<li><a class="item" href="#responses"><span class="nav-label">Responses / sec</span></a></li>');
}

function getLink(link){
    var a = link.split('/');
    return (a.length<=1)? link : a[a.length-1];
}

function expandUp(li) {
  const parentId = li.attr("data-parent");
  if (parentId != "ROOT") {
    const span = $('#' + parentId);
    const parentLi = span.parents('li').first();
    span.expand(parentLi, false);
    expandUp(parentLi);
  }
}

function setActiveMenu(){
    $('.nav a').each(function() {
      const navA = $(this)
      if(!navA.hasClass('expand-button') && navA.attr('href') == getLink(window.location.pathname)) {
        const li = $(this).parents('li').first();
        li.addClass('on');
        expandUp(li);
        return false;
    }
  });
}

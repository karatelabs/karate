
function getItemLink(item){
	return item.pathFormatted + '.html';
}

function setDetailsLinkUrl(){
    $.each(stats.contents, function (name, data) {
        $('#details_link').attr('href', getItemLink(data));
        return false;
    });
}

var MENU_ITEM_MAX_LENGTH = 50;

function menuItem(item, level, parent, group) {
    if (group)
        var style = 'group';
    else
        var style = '';

    if (item.name.length > MENU_ITEM_MAX_LENGTH) {
        var title = ' title="' + item.name + '"';
        var displayName = item.name.substr(0, MENU_ITEM_MAX_LENGTH) + '...';
    }
    else {
        var title = '';
        var displayName = item.name;
    }

    if (parent) {
    	  if (level == 0)
			      var childOfRoot = 'child-of-ROOT ';
		    else
			      var childOfRoot = '';

        var style = ' class="' + childOfRoot + 'child-of-menu-' + parent + '"';
    } else
      var style = '';

    if (group)
        var expandButton = '<span id="menu-' + item.pathFormatted + '" style="margin-left: ' + (level * 10) + 'px;" class="expand-button">&nbsp;</span>';
    else
        var expandButton = '<span id="menu-' + item.pathFormatted + '" style="margin-left: ' + (level * 10) + 'px;" class="expand-button hidden">&nbsp;</span>';

    return '<li' + style + '><div class="item">' + expandButton + '<a href="' + getItemLink(item) + '"' + title + '>' + displayName + '</a></div></li>';
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
}

function setGlobalMenu(){
    $('.nav ul').append('<li><div class="item"><a href="#active_users">Active Users</a></div></li> \
        <li><div class="item"><a href="#requests">Requests / sec</a></div></li> \
        <li><div class="item"><a href="#responses">Responses / sec</a></div></li>');
}

function getLink(link){
    var a = link.split('/');
    return (a.length<=1)? link : a[a.length-1];
}
 
function setActiveMenu(){
    $('.nav a').each(function(){
        if(!$(this).hasClass('expand-button') && $(this).attr('href') == getLink(window.location.pathname)){
            $(this).parents('li').addClass('on');
            return false;
        }
    });
}

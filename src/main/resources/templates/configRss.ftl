<#include "header.ftl">
<#include "status_messages.ftl">
<#include "navigation.ftl">

<h1>${TITLE}</h1>

<form action="${ACTION}" method="post">
<table>
<tr>
  <td valign="top">
    ${I18N_SUGGESTIONS}<br/>
    <small><a href="http://vdr-wiki.de/wiki/index.php?title=Vodcatcher_Helper/Feeds&amp;action=edit">(${I18N_ADD_SUGGESTION})</a></small>
  </td><td>
    <div id="suggestion_indicator">${I18N_LOADING_LIST}</div>
    <select id="feeds" name="feeds" size="20" multiple="multiple" style="display:none; min-width:600px; max-width:600px" class="ui-widget ui-widget-content ui-corner-all"></select>
  </td><td valign="top">
    <input id="button_add_feeds" type="submit" name="add_feeds" value="${I18N_ADD_SELECTED}" style="display:none" class="ui-button"/>
  </td>
</tr>
<tr>
  <td>
    ${I18N_ADD_NEW_FEED}
  </td><td>
    <input type="text" name="feed" style="min-width:600px; max-width:600px" class="ui-widget ui-widget-content ui-corner-all" />
  </td><td>
    <input type="submit" name="add_feed" value="${I18N_ADD}" class="ui-button" />
  </td>
</tr>
<tr>
  <td valign="top">
    ${I18N_INSTALLED_FEEDS}
  </td><td>
    <select name="feeds" size="20" multiple="multiple" style="min-width:600px; max-width:600px" class="ui-widget ui-widget-content ui-corner-all">
        <#list FEEDS as feed>
        <option value="${feed.id}">${feed.title} - ${feed.uri}</option> 
        </#list>
    </select>
  </td><td valign="top">
    <input type="submit" name="remove_feeds" value="${I18N_DELETE_SELECTED}" class="ui-button" />
  </td>
</tr>
</table>
</form>

<script type="text/javascript">
    $(document).ready(function() {
        $.getJSON('${ACTION}?get_suggestions', function(data) {
            $('#feeds').show();
            $('#button_add_feeds').show();
            $('#suggestion_indicator').hide();
            for(var i=0; i<data.length; i++) {
                var group = data[i];
                var optgroup = document.createElement('optgroup');
                optgroup.label = group.title;
                for(var j=0; j<group.feeds.length; j++) {
                    var feed = group.feeds[j];
                    var option = document.createElement('option');
                    option.value = feed.uri;
                    option.text = feed.title;
                    $(optgroup).append(option);
                }
                $('#feeds').append(optgroup);
            }
        });
    });
</script>
<#include "footer.ftl">
define('com.mesilat.data-share:inline-dialog', ['confluence/ic/util/text-highlighter'], function(TextHighlighter){
    // This is mostly copy-paste from https://developer.atlassian.com/server/confluence/extending-the-highlight-actions-panel/

    var DIALOG_MAX_HEIGHT = 200;
    var DIALOG_WIDTH = 300;
    var dialog;
    var defaultDialogOptions = {
        hideDelay: null,
        width : DIALOG_WIDTH,
        maxHeight: DIALOG_MAX_HEIGHT
    };
    var highlighter;

    function showDialog(selectionObject) {
        highlighter = highlighter || new TextHighlighter;
        highlighter.removeHighlight().highlight(selectionObject.range);
        dialog && dialog.remove();

        var displayFn = function(content, trigger, showPopup) {
            $(content).html(Mesilat.DataShare.Templates.createDialogContent({
                highlightText: selectionObject.text,
                foundNum: selectionObject.searchText.numMatches
            }))
            .on('click', 'input.button.submit', function(e){
                var attributeName = $(e.target).closest('form').find('#dsattr-name').val();
                postData(selectionObject.searchText, attributeName);
                highlighter && highlighter.removeHighlight();
                dialog && dialog.remove();
            });
            showPopup();
            return false;
        };
        dialog = _openDialog(selectionObject, 'dsattr-inline-dialog', defaultDialogOptions, displayFn);
    };

    function _openDialog(selectionObject, id, options, displayFn) {
        var $target = $("<div>");
        _appendDialogTarget(selectionObject.area.average, $target);
        var originalCallback = options.hideCallback;
        options.hideCallback = function() {
            $target.remove();
            highlighter && highlighter.removeHighlight();
            originalCallback && originalCallback();
        };
        var dialog = Confluence.ScrollingInlineDialog($target, id, displayFn, options);
        dialog.show();
        return dialog;
    };

    function _appendDialogTarget(targetDimensions, $target) {
        Confluence.DocThemeUtils.appendAbsolutePositionedElement($target);
        $target.css({
            top: targetDimensions.top,
            height: targetDimensions.height,
            left: targetDimensions.left,
            width: targetDimensions.width,
            "z-index": -9999,
            position: 'absolute'
        });
    };

    function getLastFetchTime() {
        return $("meta[name='confluence-request-time']").attr("content");
    }

    function postData(searchText, attributeName){
        var url = AJS.contextPath() + '/rest/data-share/1.0/inline',
            data = {
                index: searchText.index,
                numMatches: searchText.numMatches,
                pageId: searchText.pageId,
                selectedText: searchText.selectedText,
                attributeName: attributeName,
                lastFetchTime: getLastFetchTime()
            };
        return $.ajax({
            url: url,
            type: 'POST',
            data: JSON.stringify(data),
            contentType: 'application/json',
            processData: false,
            dataType: 'json',
            context: {
                url: url
            }
        });
    }
    
    return {
        show: showDialog
    };
});

require(['com.mesilat.data-share:inline-dialog'], function(InlineDialog){
    AJS.toInit(function($) {
        var PLUGIN_KEY = 'com.mesilat.data-share:inline-attribute';
        Confluence && Confluence.HighlightAction && Confluence.HighlightAction.registerButtonHandler(
            PLUGIN_KEY,
            {
                onClick: function(selectionObject) {
                    InlineDialog.show(selectionObject);
                },
                shouldDisplay: Confluence.HighlightAction.WORKING_AREA.MAINCONTENT_ONLY
            }
        );
    });
});
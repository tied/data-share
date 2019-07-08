define('com.mesilat:data-share',[], function(){
    return {
        objectClassName: 'dsobject',
        objectIdClassPrefix: 'dsobjid-',
        objectTypeClassPrefix: 'dsobjtype-',
        attributeNameClassPrefix: 'dsattr-'
    };
});

define('com.mesilat:data-share/util',[], function(){
    return {
        generateUUID : function () {
            var s4 = function() {
                return Math.floor((1 + Math.random()) * 0x10000)
                        .toString(16)
                        .substring(1);
            };

            return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
                    s4() + '-' + s4() + s4() + s4();
        }
    };
});

require(['ajs', 'jquery', 'com.mesilat:data-share', 'com.mesilat:data-share/util'],function(AJS, $, DataShare, Util){
    AJS.bind('init.rte', function() {
        Confluence.Editor.addSaveHandler(function(e){
            var ids = {};
            try {
                $('#wysiwygTextarea_ifr').contents().find('.' + DataShare.objectClassName).each(function(){
                    var $elt = $(this),
                        classNames = this.className.split(/\s+/),
                        id;

                    classNames.forEach(function(className){
                        if (className.startsWith(DataShare.objectIdClassPrefix)){
                            id = className;
                        }
                    });

                    if (typeof id === 'undefined'){
                        id = DataShare.objectIdClassPrefix + Util.generateUUID();
                        $elt.addClass(id);
                    } else if (id in ids){
                        $elt.removeClass(id);
                        id = DataShare.objectIdClassPrefix + Util.generateUUID();
                        $elt.addClass(id);
                    }

                    ids[id] = $elt;
                });
            } catch (e) {
                //AJS.logError('DataShare saveHandler', e);
                alert(e);
            }
	});
    });
});

define('com.mesilat:data-share/table-hook',['com.mesilat:data-share', 'com.mesilat:data-share/util'], function(DataShare, Util){
    function bind(){
        var tinymce = require('tinymce');
        //tinymce.activeEditor.onBeforeExecCommand.add(function(ed, cmd, ui, val) {
        //    if (cmd === 'mceTableCopyRow'){
        //        console.log('com.mesilat.data-share/hook mceTableCopyRow', $(ed.selection.getNode()).closest('tr'));
        //    }
        //});
        tinymce.activeEditor.onExecCommand.add(function(ed, cmd) {
            switch (cmd){
                case 'mceTablePasteRowBefore':
                case 'mceTableInsertRowBefore':
                    {
                        var $src = $(ed.selection.getNode()).closest('tr');
                        if ($src.hasClass(DataShare.objectClassName)){
                            var $dst = $src.prev();
                            $src[0].getAttribute('class').split(/\s/).forEach(function(c){
                                if (!c.startsWith(DataShare.objectIdClassPrefix)){
                                    $dst.addClass(c);
                                } else {
                                    $dst.addClass(DataShare.objectIdClassPrefix + Util.generateUUID());
                                }
                            });
                        }
                    }
                    break;
                case 'mceTablePasteRowAfter':
                case 'mceTableInsertRowAfter':
                    {
                        var $src = $(ed.selection.getNode()).closest('tr');
                        if ($src.hasClass(DataShare.objectClassName)){
                            var $dst = $src.next();
                            $src[0].getAttribute('class').split(/\s/).forEach(function(c){
                                if (!c.startsWith(DataShare.objectIdClassPrefix)){
                                    $dst.addClass(c);
                                } else {
                                    $dst.addClass(DataShare.objectIdClassPrefix + Util.generateUUID());
                                }
                            });
                        }
                    }
            }
        });
    }

    return {
        bind: bind
    };
});

require('confluence/module-exporter').safeRequire('com.mesilat:data-share/table-hook', function(Hook) {    
    require('ajs').bind('rte-ready', function() {
        Hook.bind();
    });
});


(function(){
    var $ = require('jquery');
    var document = require('document');
    var observing = false;

    function getClasses(elt){
        return elt.className.split(/\s+/);
    }
    function readClasses(elt){
        var classes = {
            dsobject: false,
            dsattrtype: '',
            dsattr: '',
            dsobjtype: '',
            other: []
        };
        getClasses(elt).forEach(function(name){
            if (name === 'dsobject'){
                classes.dsobject = true;
            } else if (name.indexOf('dsattrtype-') === 0){
                classes.dsattrtype = name.substring(11);
            } else if (name.indexOf('dsattr-') === 0){
                classes.dsattr = name.substring(7);
            } else if (name.indexOf('dsobjtype-') === 0){
                classes.dsobjtype = name.substring(10);
            } else {
                classes.other.push(name);
            }
        });
        return classes;
    }
    function updateDialog($dlg, classes){
        if (classes.dsobject){
            $dlg.find('#dsobject').prop('checked', true);
        } else {
            $dlg.find('#dsobject').prop('checked', false);
        }
        $dlg.find('#dsattrtype-type').val(classes.dsattrtype);
        $dlg.find('#dsattr-name').val(classes.dsattr);
        $dlg.find('#dsobjtype-type').val(classes.dsobjtype);
        $dlg.find('#other-classes').val(classes.other.join(' '));
        return classes;
    }
    function applyClasses(elt, classes){
        var _classes = [];
        if (classes.dsobject){
            _classes.push('dsobject');
        }
        if (classes.dsattrtype !== ''){
            _classes.push('dsattrtype-' + classes.dsattrtype);
        }
        if (classes.dsattr !== ''){
            _classes.push('dsattr-' + classes.dsattr);
        }
        if (classes.dsobjtype !== ''){
            _classes.push('dsobjtype-' + classes.dsobjtype);
        }
        _classes = _classes.concat(classes.other.filter(function(s){ return s !== ''; }));
        elt.className = _classes.join(' ');
    }
    function showDefineDialog(){
        var dlg = AJS.dialog2($(Mesilat.DataShare.Templates.defineMarkup({})))
        .on('show', function(e){
            var editor = AJS.Rte.getEditor(),
                td = editor.selection.getNode().closest('td'),
                tr = editor.selection.getNode().closest('tr'),
                table = editor.selection.getNode().closest('table'),
                tdClasses = readClasses(td),
                trClasses = readClasses(tr),
                tableClasses = readClasses(table),
                activeClasses = tdClasses;

            $(e.target).find('button.aui-button-primary').click(function(){
                console.debug('com.mesilat.data-share apply classes');
                applyClasses(td, tdClasses);
                applyClasses(tr, trClasses);
                applyClasses(table, tableClasses);
                dlg.hide();
            });
            $(e.target).find('button.aui-button-link').click(function(){
                //console.debug('com.mesilat.data-share Cancel');
                dlg.hide();
            });

            $(e.target).find('#dsobject').on('change', function(e){
                activeClasses.dsobject = $(e.target).is(':checked');
            });
            $(e.target).find('#dsattrtype-type').on('change', function(e){
                activeClasses.dsattrtype = $(e.target).val();
            });
            $(e.target).find('#dsattr-name').on('change', function(e){
                activeClasses.dsattr = $(e.target).val();
            });
            $(e.target).find('#dsobjtype-type').on('change', function(e){
                activeClasses.dsobjtype = $(e.target).val();
            });
            $(e.target).find('#other-classes').on('change', function(e){
                activeClasses.other = $(e.target).val().split(/\s+/);
            });

            $(e.target).find('#table-element-td').click(function(){
                activeClasses = updateDialog($(e.target), tdClasses);
            });
            $(e.target).find('#table-element-tr').click(function(){
                activeClasses = updateDialog($(e.target), trClasses);
            });
            $(e.target).find('#table-element-table').click(function(){
                activeClasses = updateDialog($(e.target), tableClasses);
            });

            activeClasses = updateDialog($(e.target), tdClasses);
        }).show();
    }
    
    $(document).bind('initContextToolbars.Toolbar', function(e, editor){
        setTimeout(function(){
            if (Confluence.Editor.tableToolbar){
                Confluence.Editor.tableToolbar.Buttons.push(
                    new Confluence.Editor.Toolbar.Components.Group([
                        new Confluence.Editor.Toolbar.Components.Button({
                            text: 'Data share',//getLang('table.del'),
                            iconClass: 'aui-icon aui-icon-small aui-iconfont-macro-code',
                            click: function() {
                                //console.debug('com.mesilat.data-share My button');
                                showDefineDialog();
                            }
                        })
                    ])
                );
            } else /* Workaround */ if (!observing) {
                observing = true;

                function installDataShareButton($removeTableButton){
                    //var $removeTableButton = $('toolbar-split.toolbar-contextual span.aui-iconfont-remove-table');
                    //if ($removeTableButton.length === 0){
                    //    console.warn('com.mesilat.data-share', 'Confluence.Editor.tableToolbar was not found and workaround did not help; can\'t create Data Share button');
                    //    return;
                    //}
                    var $row = $removeTableButton.closest('.toolbar-split-row');
                    if ($row.find('.data-share-context-button').length > 0){
                        return;
                    }
                    var $btn = $(Mesilat.DataShare.Templates.toolbarButton({}));
                    $btn.appendTo($row);
                    $btn.find('button').on('click', function(){
                        showDefineDialog();
                    });
                    console.debug('com.mesilat.data-share', 'Data Share button installed successfully');
                }
                
                
                var MutationObserver = require('com.mesilat.mutation-observer');
                var observer = new MutationObserver(function(mutations){
                    mutations.forEach(function(mutation){
                        var $nodes = $(mutation.addedNodes);
                        $nodes.find('span.aui-iconfont-remove-table').each(function(){
                            installDataShareButton($(this));
                        });
                    });
                });
                observer.observe(document, {
                    childList: true,
                    subtree: true
                });
            }
        });
    });
})();
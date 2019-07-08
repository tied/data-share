(function(AJS, $){
    var data;

    function checkNotifications(timeout){
        if (timeout > 10000){
            return;
        }
        setTimeout(function(){
            $.ajax({
                url: AJS.contextPath() + '/plugins/servlet/data-share/notifications',
                type: 'GET',
                dataType: 'json',
                data: data || {
                        'page-id': AJS.params.pageId
                        //version: AJS.params.pageVersion
                    }
            }).done(function(_data){
                data = _data;
                if (data.status){
                    switch (data.status){
                        case 'ERROR':
                        case 'WARNING':
                            AJS.flag({
                                type: data.status.toLowerCase(),
                                title: AJS.I18n.getText('com.mesilat.data-share.plugin.name'),
                                body: Mesilat.DataShare.Templates.notify({
                                    notifications: data.notifications,
                                    status: data.status
                                })
                            });
                    }
                } else {
                    checkNotifications(timeout + 1000);
                }
            }).fail(function(jqxhr){
                console.error('com.mesilat.data-share notifications error', jqxhr);
            });
        }, timeout);
    }

    AJS.toInit(function(){
        checkNotifications(1000/*sec*/);
    });
})(AJS, AJS.$||$);

(function(AJS, $){
    var data;

    function errorMessage($div, jqxhr){
        var message = jqxhr.responseText.substring(0,255);
        try {
            message = JSON.parse(jqxhr.responseText).message;
        } catch(e){}
        AJS.messages.error($div.empty(), {
            title: 'Query failed',
            body: Mesilat.DataShare.Templates.errorBody({ text: message })
        });
    }
    function normalizeData(data){
        _.keys(data).forEach(function(key){
            if (data[key] === null || data[key] === 'null'){
                delete data[key];
            } else {
                data[key] = _.isArray(data[key])? data[key]: [data[key]];
                data[key].forEach(function(obj){
                    if (_.isObject(obj) && !('_type' in obj)){
                        normalizeData(obj);
                    }
                })
            }
        });
    }
    function setupMacros(){
        $('.com-mesilat-datashare-pages-report').each(function(){
            var $div = $(this);

            $.ajax({
                url: AJS.contextPath() + '/rest/api/content/search',
                type: 'GET',
                dataType: 'json',
                data: {
                    cql: $div.data('cql'),
                    limit: 999
                }
            })
            .done(function(data){
                var pages = [];
                data.results.forEach(function(page){
                    pages.push(page.id);
                });
                //console.log('com.mesilat.data-share pages-report', data);
                
                $.ajax({
                    url: AJS.contextPath() + '/rest/data-share/1.0/pages',
                    type: 'POST',
                    dataType: 'json',
                    data: JSON.stringify(pages),
                    contentType: 'application/json',
                    processData: false,            
                })
                .done(function(data){
                    var columns = $div.data('columns') === ''? ['page']: $div.data('columns').split(','),
                        titles = $div.data('titles') === ''? ['Page']: $div.data('titles').split(','),
                        _titles = {};
                    for (var i = 0; i < columns.length; i++){
                        if (titles.length > i){
                            _titles[columns[i]] = titles[i];
                        }
                    }

                    console.debug('com.mesilat.data-share pages-report', data, columns, _titles);
                    data.forEach(function(rec){
                        normalizeData(rec.data);
                    });
                    $div.empty().html(
                        Mesilat.DataShare.Templates.reportTable({
                            columns: columns,
                            titles: _titles,
                            data: data
                        })
                    );
                })
                .fail(function(jqxhr){
                    errorMessage($div, jqxhr);
                });
            })
            .fail(function(jqxhr){
                errorMessage($div, jqxhr);
            });
        });
    }

    AJS.toInit(function(){
        setupMacros();
    });
})(AJS, AJS.$||$);
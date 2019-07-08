package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.core.PartialList;
import com.atlassian.confluence.labels.LabelManager;
import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.mesilat.datashare.events.DataObjectCreateEvent;
import com.mesilat.datashare.events.DataObjectDeleteEvent;
import com.mesilat.datashare.events.DataObjectUpdateEvent;
import com.mesilat.datashare.service.PathEvaluationService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@ExportAsService ({PathEvaluationService.class})
@Named
public class PathEvaluationServiceImpl implements PathEvaluationService, InitializingBean, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    public static final int MAX_PAGES = 1000000;

    @ComponentImport
    private final EventPublisher eventPublisher;
    @ComponentImport
    private final ActiveObjects ao;
    @ComponentImport
    private final LabelManager labelManager;
    @ComponentImport
    private final PageManager pageManager;
    private final String baseUrl;

    private final Map<String,Object> cache = new HashMap<>();
    private final Map<String,List<String>> labelIndex = new HashMap<>();
    private final Map<Long,List<String>> pageIndex = new HashMap<>();
    private final List<String> allIndex = new ArrayList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
        LOGGER.debug("Started listening data object events");
    }
    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
        LOGGER.debug("Stopped listening data object events");
    }
    @Override
    public Object evaluate(String path) {
        String key = getKey(path);
        synchronized(this){
            if (cache.containsKey(key)){
                return cache.get(key);
            }
        }

        ArrayList arr = new ArrayList();
        Arrays.asList(ao.find(DataObject.class)).forEach(obj -> {
            try {
                Object o = _evaluate(obj.getRootObject(), path);
                if (o != null && !(o instanceof JsonNull)){
                    arr.add(_result(o, obj.getId()));
                }
            } catch(Throwable ex){
                LOGGER.warn(String.format("Evaluate pageId=%d, path=%s failed", obj.getId(), path), ex);
            }
        });

        if (arr.isEmpty()){
            return null;
        } else if (arr.size() == 1) {
            synchronized(this){
                cache.put(key, arr.get(0));
                allIndex.add(key);
                return arr.get(0);
            }
        } else {
            synchronized(this){
                cache.put(key, arr);
                allIndex.add(key);
                return arr;
            }
        }
    }

    @Override
    public Object evaluate(Long pageId, String path) {
        LOGGER.debug(String.format("Evaluate pageId=%d, path=%s", pageId, path));

        String key = getKey(pageId, path);
        synchronized(this){
            if (cache.containsKey(key)){
                return cache.get(key);
            }
        }
        DataObject obj = ao.get(DataObject.class, pageId);
        if (obj == null){
            return null;
        } else {
            Object o = _evaluate(obj.getRootObject(), path);
            if (o == null || o instanceof JsonNull){
                return null;
            } else {
                synchronized(this){
                    Object _o = _result(o, obj.getId());
                    cache.put(key, _o);
                    if (!pageIndex.containsKey(pageId)){
                        pageIndex.put(pageId, new ArrayList<>());
                    }
                    pageIndex.get(pageId).add(key);
                    return _o;
                }
            }
        }
    }
    @Override
    public Object evaluate(String label, String path) {
        LOGGER.debug(String.format("Evaluate label=%s, path=%s", label, path));

        String key = getKey(label, path);
        synchronized(cache){
            if (cache.containsKey(key)){
                return cache.get(key);
            }
        }

        Label l = labelManager.getLabel(label);
        if (l == null){
            return null;
        }
        PartialList<ContentEntityObject> content = labelManager.getContentForLabel(0, MAX_PAGES, l);
        if (content == null || content.getCount() == 0){
            return null;
        }

        ArrayList arr = new ArrayList();
        content.getList().forEach(p -> {
            DataObject obj = ao.get(DataObject.class, p.getId());
            if (obj != null){
                try {
                    Object o = _evaluate(obj.getRootObject(), path);
                    if (o != null && !(o instanceof JsonNull)){
                        arr.add(_result(o, obj.getId()));
                    }
                } catch(Throwable ex){
                    LOGGER.warn(String.format("Evaluate pageId=%d, path=%s failed", p.getId(), path), ex);
                }
            }
        });

        if (arr.isEmpty()){
            return null;
        } else if (arr.size() == 1) {
            synchronized(this){
                cache.put(key, arr.get(0));
                if (!labelIndex.containsKey(label)){
                    labelIndex.put(label, new ArrayList<>());
                }
                labelIndex.get(label).add(key);
                return arr.get(0);
            }
        } else {
            synchronized(this){
                cache.put(key, arr);
                if (!labelIndex.containsKey(label)){
                    labelIndex.put(label, new ArrayList<>());
                }
                labelIndex.get(label).add(key);
                return arr;
            }
        }
    }

    private static String getKey(String path){
        return path;
    }
    private static String getKey(Long pageId, String path){
        return String.format("%d: %s", pageId, path);
    }
    private static String getKey(String label, String path){
        return String.format("%s: %s", label, path);
    }

    private Object _evaluate(String text, String path){
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(text);
        if (path == null || path.isEmpty()){
            return JsonPath.read(document, "$");
        } else {
            return JsonPath.read(document, "$." + path);
        }
    }
    private JsonObject _result(Object val, Long id){
        JsonObject src = new JsonObject();
        src.addProperty("id", id);
        Page page = pageManager.getPage(id);
        if (page != null){
            src.addProperty("title", page.getTitle());

            JsonArray labels = new JsonArray();
            page.getLabels().forEach(l -> labels.add(new JsonPrimitive(l.getDisplayTitle())));
            if (labels.size() != 0){
                src.add("labels", labels);
            }
        }
        src.addProperty("href", String.format("%s/rest/data-share/1.0/page/%d", baseUrl, id));
        src.addProperty("view", String.format("%s/pages/viewpage.action?pageId=%d", baseUrl, id));

        if (val instanceof JsonElement){
            src.add("value", (JsonElement)val);
        } else if (val instanceof String){
            src.addProperty("value", (String)val);
        } else if (val instanceof Number){
            src.addProperty("value", (Number)val);
        } else if (val instanceof Boolean){
            src.addProperty("value", (Boolean)val);
        } else if (val instanceof Character){
            src.addProperty("value", (Character)val);
        } else {
            src.addProperty("value", String.format("#Unexpected value type: %s", val.getClass().getName()));
        }
        return src;
    }
    private void _clearCache(Page page){
        // Always clear cache for 'all pages' query
        allIndex.forEach(key -> cache.remove(key));
        allIndex.clear();

        // Clear cache for this page queries
        if (pageIndex.containsKey(page.getId())){
            pageIndex.get(page.getId()).forEach(key -> cache.remove(key));
        }
        pageIndex.remove(page.getId());

        // Clear cache for page's labels queries
        page.getLabels().forEach(label -> {
            if (labelIndex.containsKey(label.getDisplayTitle())){
                labelIndex.get(label.getDisplayTitle()).forEach(key -> cache.remove(key));
            }
            labelIndex.remove(label.getDisplayTitle());
        });
    }

    @EventListener
    public void dataObjectCreateEvent(DataObjectCreateEvent event) {
        synchronized(this){
            _clearCache((Page)event.getSource());
        }
    }
    @EventListener
    public void dataObjectUpdateEvent(DataObjectUpdateEvent event) {
        synchronized(this){
            _clearCache((Page)event.getSource());
        }
    }
    @EventListener
    public void dataObjectDeleteEvent(DataObjectDeleteEvent event) {
        synchronized(this){
            _clearCache((Page)event.getSource());
        }
    }
    
    @Inject
    public PathEvaluationServiceImpl(ActiveObjects ao, LabelManager labelManager,
        EventPublisher eventPublisher, PageManager pageManager,
        @ComponentImport SettingsManager settingsManager
    ){
        this.ao = ao;
        this.labelManager = labelManager;
        this.eventPublisher = eventPublisher;
        this.baseUrl = settingsManager.getGlobalSettings().getBaseUrl();
        this.pageManager = pageManager;
    }
}
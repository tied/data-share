package com.mesilat.datashare;

import com.mesilat.datashare.events.DataObjectUpdateEvent;
import com.mesilat.datashare.events.DataObjectDeleteEvent;
import com.mesilat.datashare.events.DataObjectCreateEvent;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.content.render.xhtml.DefaultXmlEventReaderFactory;
import com.atlassian.confluence.content.render.xhtml.XmlEventReaderFactory;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.sal.api.user.UserManager;
import com.google.common.collect.ImmutableMap;
import com.mesilat.datashare.events.DataObjectEvent;
import com.mesilat.datashare.parser.PageParser3;
import com.mesilat.datashare.service.PageProcessingTask;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.stream.XMLEventReader;
import net.java.ao.DBParam;
import net.java.ao.schema.StringLength;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageProcessor {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    private final ActiveObjects ao;
    private final EventPublisher eventPublisher;
    private final UserManager userManager;
    private final PageManager pageManager;

    protected void savePage(long pageId, int version, boolean pageRenamed, PageProcessingTask task){
        try {
            Page page = pageManager.getPage(pageId);
            LOGGER.debug(String.format("invoked for %d: %s", pageId, page.getTitle()));

            ContentEntityObject ceo = pageManager.getOtherVersion(page, version);
            try (StringReader sr = new StringReader("<div>" + (ceo == null? page.getBodyAsString(): ceo.getBodyAsString())  + "</div>")){
                XmlEventReaderFactory xmlEventReaderFactory = new DefaultXmlEventReaderFactory();
                XMLEventReader reader = xmlEventReaderFactory.createStorageXmlEventReader(sr);
                PageParser3 parser = new PageParser3(userManager, pageManager, page.getSpaceKey(), reader);
                parser.parse();

                if (parser.hasObjects()){
                    AtomicReference<DataObjectEvent> doe = new AtomicReference<>();
                    AtomicReference<String> json = new AtomicReference<>();

                    ao.executeInTransaction(()->{
                        try {
                            DataObject dobj = ao.get(DataObject.class, page.getId());
                            ObjectMapper mapper = new ObjectMapper();
                            json.set(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parser.getRootObject()));
                            if (dobj == null){
                                LOGGER.debug("dobj == null, creating new object");
                                // New data object created
                                ao.create(DataObject.class,
                                    ImmutableMap.<String,Object>builder()
                                    .put("ID", page.getId())
                                    .put("ROOT_OBJECT", json.get())
                                    .build()
                                );
                                doe.set(new DataObjectCreateEvent(page, (ObjectNode)mapper.readTree(json.get()), task));
                            } else {
                                LOGGER.debug("dobj != null, updating object, pageRenamed=%s" + pageRenamed);
                                // Data object updated
                                ObjectNode oldObject = (ObjectNode)mapper.readTree(dobj.getRootObject());
                                dobj.setRootObject(json.get());
                                dobj.save();
                                doe.set(new DataObjectUpdateEvent(page, (ObjectNode)mapper.readTree(json.get()), oldObject, pageRenamed, task));
                            }
                        } catch (Throwable ex) {
                            LOGGER.error(String.format("Error processing page %d: %s", page.getId(), page.getTitle()), ex);
                            task.addNotification(PageProcessingTask.Status.ERROR, ex.getMessage());
                        }

                        ao.deleteWithSQL(DataAttribute.class, "PAGE_ID = ?", pageId);
                        saveAttributes(pageId, "", parser.getRootObject());

                        return null;
                    });

                    if (doe.get() != null){
                        eventPublisher.publish(doe.get());
                    }
                }
            }
        } catch (Throwable ex) {
            LOGGER.error(String.format("Error processing page %d", pageId), ex);
            task.addNotification(PageProcessingTask.Status.ERROR, ex.getMessage());
        }
    }
    protected void deletePage(long pageId, int version, PageProcessingTask task){
        try {
            Page page = pageManager.getPage(pageId);
            AtomicReference<DataObjectEvent> doe = new AtomicReference<>();

            ao.executeInTransaction(()->{
                DataObject dobj = ao.get(DataObject.class, pageId);
                if (dobj != null){
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        ObjectNode oldObject = (ObjectNode)mapper.readTree(dobj.getRootObject());
                        ao.delete(dobj);
                        doe.set(new DataObjectDeleteEvent(page, oldObject, task));
                    } catch (IOException ex) {
                        LOGGER.error("Error reading JSON object", ex);
                        task.addNotification(PageProcessingTask.Status.ERROR, ex.getMessage());
                    }
                }
                return null;
            });

            if (doe.get() != null){
                eventPublisher.publish(doe.get());
            }
        } catch (Throwable ex) {
            LOGGER.error(String.format("Error processing page %d", pageId), ex);
            task.addNotification(PageProcessingTask.Status.ERROR, ex.getMessage());
        }
    }
    protected void saveAttributes(Long pageId, String path, JsonNode node){
        if (node.isArray()){
            ArrayNode arr = (ArrayNode)node;
            arr.forEach(elt -> {
                saveAttributes(pageId, path, elt);
            });
        } else if (node.isObject()){
            ObjectNode obj = (ObjectNode)node;
            obj.getFields().forEachRemaining(e -> {
                saveAttributes(pageId, String.format("%s/%s", path, e.getKey()), e.getValue());
            });
        } else {
            if (!node.isNull()){
                String value = node.asText().trim();
                if (value.length() > StringLength.MAX_LENGTH){
                    value = value.substring(0, StringLength.MAX_LENGTH);
                }
                ao.create(DataAttribute.class, new DBParam("PAGE_ID", pageId), new DBParam("PATH", path), new DBParam("VALUE", value));
            }
        }
    }

    public PageProcessor(
        ActiveObjects ao,
        EventPublisher eventPublisher,
        UserManager userManager,
        PageManager pageManager
    ){
        this.ao = ao;
        this.eventPublisher = eventPublisher;
        this.userManager = userManager;
        this.pageManager = pageManager;
    }
}
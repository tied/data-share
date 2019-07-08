package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.plugins.highlight.SelectionModificationException;
import com.atlassian.confluence.plugins.highlight.SelectionStorageFormatModifier;
import com.atlassian.confluence.plugins.highlight.model.TextSearch;
import com.atlassian.confluence.plugins.highlight.model.XMLModification;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.ImmutableMap;
import com.mesilat.datashare.events.DataObjectCreateEvent;
import com.mesilat.datashare.events.DataObjectDeleteEvent;
import com.mesilat.datashare.events.DataObjectUpdateEvent;
import com.mesilat.datashare.service.PageProcessorService;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.java.ao.DBParam;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig.Feature;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Path("/")
@Scanned
public class DataResource extends DataResourceBase {
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    private static final Integer LIMIT = 10000000;

    @ComponentImport
    private final ActiveObjects ao;
    @ComponentImport
    private final EventPublisher eventPublisher;
    @ComponentImport
    private final PageManager pageManager;
    @ComponentImport
    private final SelectionStorageFormatModifier selectionStorageFormatModifier;
    private final PageProcessorService pageProcessorService;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public final static AtomicLong AL = new AtomicLong(-1);

    /**
     * Get single page data
     * @param id Page ID
     * @return JSON object
     */
    @GET
    @Path("page/{id}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response get(@PathParam("id") Long id){
        if (id != null) {
            DataObject obj = ao.get(DataObject.class, id);
            if (obj == null){
                return Response.status(Response.Status.NOT_FOUND).entity("The requested root data object could not be found").build();
            } else {
                return Response.ok(obj.getRootObject()).build();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please provide page id to get root data object").build();
        }
    }

    /**
     * Get multiple pages
     * @param ids Page IDS comma separated
     * @return JSON array
     * @throws java.io.IOException
     */
    @GET
    @Path("pages")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response get(@QueryParam("id") List<String> ids) throws IOException {
        if (ids == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please provide pages' ids to get data objects").build();
        }
        ArrayNode arr = mapper.createArrayNode();
        ids.forEach(id -> {
            DataObject o = ao.get(DataObject.class, Long.parseLong(id));
            if (o != null){
                Page page = pageManager.getPage(o.getId());
                ObjectNode obj = mapper.createObjectNode();
                obj.put("id", o.getId());
                obj.put("title", page == null? "Page not found": page.getTitle());
                obj.put("href", String.format("%s/rest/data-share/1.0/page/%s", baseUrl, id));
                obj.put("view", String.format("%s/pages/viewpage.action?pageId=%s", baseUrl, id));
                try {
                    obj.put("data", mapper.readTree(o.getRootObject()));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                arr.add(obj);
            }
        });
        return Response.ok(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr)).build();
    }

    /**
     * Get multiple pages
     * @param ids Page IDS in JSON array
     * @return JSON array
     * @throws java.io.IOException
     */
    @POST
    @Path("pages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response getMany(ArrayNode ids) throws IOException {
        List<String> _ids = new ArrayList<>();
        ids.forEach(id -> _ids.add(id.asText()));
        return get(_ids);
    }

    /**
     * Delete page data
     * @param id Page ID
     * @return Nothing
     */
    @DELETE
    @Path("page/{id}")
    public Response delete(@PathParam("id") Long id){
        if (id != null) {
            DataObject obj = ao.get(DataObject.class, id);
            if (obj == null){
                return Response.status(Response.Status.NOT_FOUND).entity("The requested root data object could not be found").build();
            } else {
                Page page = pageManager.getPage(obj.getId());
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode oldObject = null;
                try {
                    oldObject = (ObjectNode)mapper.readTree(obj.getRootObject());
                } catch (IOException ex) {
                    LOGGER.warn(String.format("Failed to parse object %s", obj.getRootObject()), ex);
                }
                ao.delete(obj);
                if (oldObject != null){
                    DataObjectDeleteEvent evt = new DataObjectDeleteEvent(page, oldObject, null);
                    eventPublisher.publish(evt);
                }
                return Response.status(Response.Status.ACCEPTED).build();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please provide page id to root data object").build();
        }
    }

    /**
     * Overwrite page data
     * @param id Page ID
     * @param data Data
     * @return Nothing
     * @throws java.io.IOException
     */
    @POST
    @Path("page/{id}")
    @Consumes(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response post(@PathParam("id") Long id, String data) throws IOException{
        if (id != null) {
            ObjectNode _obj = (ObjectNode)mapper.readTree(data);
            String _data = mapper.writeValueAsString(_obj);
            return ao.executeInTransaction(()->{
                try {
                    DataObject obj = ao.get(DataObject.class, id);
                    Page page = pageManager.getPage(id);
                    ObjectMapper mapper = new ObjectMapper();
                    if (obj == null){
                        DataObjectCreateEvent evt = new DataObjectCreateEvent(page, (ObjectNode)mapper.readTree(data), null);
                        obj = ao.create(DataObject.class, new DBParam("ID", id));
                        obj.setRootObject(_data);
                        obj.save();
                        eventPublisher.publish(evt);
                    } else {
                        ObjectNode oldObject = null;
                        try {
                            oldObject = (ObjectNode)mapper.readTree(obj.getRootObject());
                        } catch (IOException ex) {
                            LOGGER.warn(String.format("Failed to parse object %s", obj.getRootObject()), ex);
                        }
                        DataObjectUpdateEvent evt = new DataObjectUpdateEvent(page, _obj, oldObject, false, null);
                        obj.setRootObject(_data);
                        obj.save();
                        eventPublisher.publish(evt);
                    }
                    return Response.status(Response.Status.ACCEPTED).build();
                } catch (IOException ex) {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                }
            });
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please provide page id to root data object").build();
        }
    }

    /**
     * Same as POST above
     * @param id Page ID
     * @param data Data
     * @return Nothing
     * @throws java.io.IOException
     */
    @PUT
    @Path("page/{id}")
    @Consumes(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response put(@PathParam("id") Long id, String data) throws IOException{
        return post(id, data);
    }

    /**
     * Trigger processing of a page
     * Usage: curl -u <user>:<passwd> -X POST -H "X-Atlassian-Token: no-check" https://<confluence base>/rest/data-share/1.0/bounce/<id страницы>
     * @param id Page ID
     * @return Nothing
     */
    @POST
    @Path("bounce/{id}")
    public Response bounce(@PathParam("id") Long id){
        if (id != null) {
            return ao.executeInTransaction(()->{
                try {
                    DataObject obj = ao.get(DataObject.class, id);
                    Page page = pageManager.getPage(id);
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode root = (ObjectNode)mapper.readTree(obj.getRootObject());
                    DataObjectUpdateEvent evt = new DataObjectUpdateEvent(page, root, root, false, null);
                    eventPublisher.publish(evt);
                    return Response.status(Response.Status.ACCEPTED).entity("OK").build();
                } catch (IOException ex) {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                }
            });
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please provide page id").build();
        }
    }

    /**
     * Lookup pages with data
     * @param q Text to search in page title
     * @param labels Page labels
     * @param limit Limit
     * @return JSON Array
     */
    @GET
    @Path("find")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response find(@QueryParam("q") String q, @QueryParam("label") List<String> labels, @QueryParam("limit") Integer limit){
        int _limit = limit == null? LIMIT: limit;

        return ao.executeInTransaction(()->{
            DataObject dobj = ao.create(DataObject.class, ImmutableMap.<String,Object>builder()
                .put("ID", AL.decrementAndGet())
                .build()
            );

            try (Connection conn = dobj.getEntityManager().getProvider().getConnection()) {
                StringBuilder sb = new StringBuilder();
                if (labels == null || labels.isEmpty()){
                    return Response.ok(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(find(conn, q, _limit))).build();
                } else {
                    return Response.ok(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(find(conn, q, labels, _limit))).build();
                }
            } catch(SQLException | IOException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            } finally {
                ao.delete(dobj);
            }
        });
    }

    /**
     * Lookup pages with data (slightly different)
     * @param q Text to search in page title
     * @param labels Page labels
     * @param limit Limit
     * @return JSON Array
     */
    @GET
    @Path("find2")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response find2(@QueryParam("q") String q, @QueryParam("label") List<String> labels, @QueryParam("limit") Integer limit){
        int _limit = limit == null? LIMIT: limit;

        return ao.executeInTransaction(()->{
            DataObject dobj = ao.create(DataObject.class, ImmutableMap.<String,Object>builder()
                .put("ID", AL.decrementAndGet())
                .build()
            );

            try (Connection conn = dobj.getEntityManager().getProvider().getConnection()) {
                StringBuilder sb = new StringBuilder();
                if (labels == null || labels.isEmpty()){
                    return Response.ok(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(find(conn, q, _limit))).build();
                } else {
                    return Response.ok(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(find2(conn, q, labels, _limit))).build();
                }
            } catch(SQLException | IOException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            } finally {
                ao.delete(dobj);
            }
        });
    }

    /**
     * Creates "inline" data attribute on page
     * @param attributeBean Attribute data
     * @return Message
     */
    @POST
    @Path("inline")
    public Response inline(InlineAttributeBean attributeBean){
        try {
            if (selectionStorageFormatModifier.markSelection(
                attributeBean.getPageId(),
                attributeBean.getLastFetchTime(),
                new TextSearch(attributeBean.getSelectedText(), attributeBean.getNumMatches(), attributeBean.getIndex()),
                new XMLModification(String.format("<span class=\"dsattr-%s\" />", attributeBean.getAttributeName()))
            )){
                LOGGER.debug(String.format("Successfully added attribute %s to page %d", attributeBean.getAttributeName(), attributeBean.getPageId()));
                Page page = pageManager.getPage(attributeBean.getPageId());
                pageProcessorService.processSavePage(page, false, AuthenticatedUserThreadLocal.get());
                return Response.status(Response.Status.OK).entity("Attribute added successfully").build();
            } else {
                LOGGER.warn(String.format("Failed to add attribute %s to page %d, selectionStorageFormatModifier.markSelection() returned \"false\"", attributeBean.getAttributeName(), attributeBean.getPageId()));
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Attribute add failed").build();
            }
        } catch (SAXException | SelectionModificationException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    public DataResource(
        ActiveObjects ao,
        EventPublisher eventPublisher,
        PageManager pageManager,
        SettingsManager settingsManager,
        SelectionStorageFormatModifier selectionStorageFormatModifier,
        PageProcessorService pageProcessorService
    ){
        super(settingsManager.getGlobalSettings().getBaseUrl());
        this.ao = ao;
        this.eventPublisher = eventPublisher;
        this.pageManager = pageManager;
        this.baseUrl = settingsManager.getGlobalSettings().getBaseUrl();
        this.selectionStorageFormatModifier = selectionStorageFormatModifier;
        this.pageProcessorService = pageProcessorService;

        this.mapper.enable(Feature.INDENT_OUTPUT);
    }
}
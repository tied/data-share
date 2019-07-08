package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import net.java.ao.DBParam;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

@Scanned
public class DataShareContentFilter implements Filter {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");
    private static final Pattern RE_OBJID = Pattern.compile("dsobjid-([0-9a-f\\-]+)");
    private final ObjectMapper mapper = new ObjectMapper();

    @ComponentImport
    private final ActiveObjects ao;

    @Override
    public void init(FilterConfig filterConfig)throws ServletException{
    }
    @Override
    public void destroy(){
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)throws IOException,ServletException{
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse res = (HttpServletResponse)response;
        switch (req.getMethod()){
            case "POST":
                {
                    // It is important to prevent duplicate object IDS
                    MyHttpServletResponseWrapper responseWrapper = new MyHttpServletResponseWrapper(res);
                    byte[] data = IOUtils.toByteArray(request.getInputStream());
                    ObjectNode obj = (ObjectNode)mapper.readTree(data);
                    if (
                        obj.has("type")
                        && "page".equals(obj.get("type").asText())
                        && obj.has("body")
                        && obj.get("body").has("editor")
                        && obj.get("body").get("editor").has("value")
                        && obj.get("body").get("editor").get("value").isTextual()
                    ){
                        Long draftId = Long.parseLong(obj.get("id").asText());
                        LOGGER.debug("Perfom page preprocessing...");
                        ObjectNode editor = (ObjectNode)obj.get("body").get("editor");
                        String html = normalizeObjectIds(draftId, editor.get("value").asText());
                        editor.put("value", html);
                        data = mapper.writeValueAsBytes(obj);
                        LOGGER.debug("Page preprocessing complete");
                        chain.doFilter(new MyHttpServletRequestWrapper(req, data), responseWrapper);

                        String json = responseWrapper.getCaptureAsString();
                        try {
                            obj = (ObjectNode)mapper.readTree(json);
                            Long pageId = Long.parseLong(obj.get("id").asText());
                            if (!draftId.equals(pageId)){
                                DataInlineObject[] ddio = ao.find(DataInlineObject.class, "PAGE_ID = ?", draftId);
                                for (DataInlineObject dio : ddio){
                                    dio.setPageId(pageId);
                                    dio.save();
                                }
                            }
                        } catch(Throwable ex){
                            LOGGER.error("Failure after preprocessing", ex);
                        }
                        response.getWriter().write(json);                
                    } else {
                        chain.doFilter(new MyHttpServletRequestWrapper(req, data), response);
                    }
                }
                break;
            case "PUT":
                {
                    byte[] data = IOUtils.toByteArray(request.getInputStream());
                    ObjectNode obj = (ObjectNode)mapper.readTree(data);
                    if (
                        obj.has("type")
                        && "page".equals(obj.get("type").asText())
                        && obj.has("body")
                        && obj.get("body").has("editor")
                        && obj.get("body").get("editor").has("value")
                        && obj.get("body").get("editor").get("value").isTextual()
                    ){
                        Long pageId = Long.parseLong(obj.get("id").asText());
                        LOGGER.debug("Perfom page preprocessing...");
                        ObjectNode editor = (ObjectNode)obj.get("body").get("editor");
                        String html = normalizeObjectIds(pageId, editor.get("value").asText());
                        editor.put("value", html);
                        data = mapper.writeValueAsBytes(obj);
                        LOGGER.debug("Page preprocessing complete");
                        chain.doFilter(new MyHttpServletRequestWrapper(req, data), response);
                    } else {
                        chain.doFilter(new MyHttpServletRequestWrapper(req, data), response);
                    }
                }
                break;
            default:
                chain.doFilter(request, response);
        }
    }
    private String normalizeObjectIds(Long pageId, String html){
        int n = 0;
        Matcher m = RE_OBJID.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        Map<String,Boolean> map = new HashMap<>();
        while (m.find()){
            String objid = m.group(1);
            //LOGGER.debug(String.format("Found objid=%s", objid));

            if (map.containsKey(objid)){
                objid = UUID.randomUUID().toString();//.toLowerCase();
                n++;
                registerInlineObject(pageId, objid);
            } else {
                DataInlineObject dio = ao.get(DataInlineObject.class, objid);
                if (dio == null){
                    registerInlineObject(pageId, objid);
                } else if (!pageId.equals(dio.getPageId())){
                    objid = UUID.randomUUID().toString();//.toLowerCase();
                    n++;
                    registerInlineObject(pageId, objid);
                }
            }

            m.appendReplacement(sb, String.format("dsobjid-%s", objid));
            map.put(objid, Boolean.TRUE);
        }
        m.appendTail(sb);
        LOGGER.debug(String.format("Number of objid replacements made: %d", n));
        return sb.toString();
    }
    private void registerInlineObject(long pageId, String objid){
        DataInlineObject dio = ao.create(DataInlineObject.class, new DBParam("ID", objid));
        dio.setPageId(pageId);
        dio.save();
    }

    @Inject
    public DataShareContentFilter(ActiveObjects ao){
        this.ao = ao;
    }
}
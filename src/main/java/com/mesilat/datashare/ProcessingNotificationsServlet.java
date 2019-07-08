package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import static com.mesilat.datashare.PageProcessorServiceImpl.STATUS_ERRORS;
import static com.mesilat.datashare.PageProcessorServiceImpl.STATUS_SUCCESS;
import static com.mesilat.datashare.PageProcessorServiceImpl.STATUS_WARNING;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import javax.inject.Inject;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingNotificationsServlet extends HttpServlet {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    @ComponentImport
    private final ActiveObjects ao;
    @ComponentImport
    private final PageManager pageManager;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");

        long pageId = Long.parseLong(req.getParameter("page-id"));
        int version = req.getParameter("version") == null? pageManager.getPage(pageId).getVersion()
                : Integer.parseInt(req.getParameter("version"));

        ObjectNode result = notask(pageId, version);
        DataProcessorQueue[] q = ao.find(DataProcessorQueue.class,
            "PAGE_ID = ? and VERSION = ? and USER_KEY = ? and ACKNOWLEDGED = 'NACK' and STATUS not in ('PEND','PROC')",
            pageId, version, AuthenticatedUserThreadLocal.get().getKey().getStringValue()
        );

        if (q.length > 0){
            switch(q[0].getStatus()){
                case STATUS_SUCCESS:
                    result.put("status", "SUCCESS");
                    break;
                case STATUS_WARNING:
                    result.put("status", "WARNING");
                    break;
                case STATUS_ERRORS:
                    result.put("status", "ERROR");
            }
            result.putPOJO("notifications", q[0].getLog() == null? new ArrayList(): q[0].getLog().split("\n"));
            q[0].setAcknowledged("ACKD");
            q[0].save();
        }

        try (PrintWriter pw = resp.getWriter()){
            mapper.writerWithDefaultPrettyPrinter().writeValue(pw, result);
        }
    }

    @Inject
    public ProcessingNotificationsServlet(ActiveObjects ao, PageManager pageManager){
        this.ao = ao;
        this.pageManager = pageManager;
    }

    private ObjectNode notask(long pageId, int version){
        ObjectNode obj = mapper.createObjectNode();
        obj.put("page-id", pageId);
        obj.put("version", version);
        return obj;
    }
}
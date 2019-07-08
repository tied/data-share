package com.mesilat.datashare;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.mesilat.datashare.service.PathEvaluationService;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathServlet extends HttpServlet {

    private static final Pattern PAGE_PATH = Pattern.compile("^/([0-9]+?)(/(.*))?$");
    private static final Pattern LABEL_PATH = Pattern.compile("^/([^/]+?)(/(.*))?$");

    private final PathEvaluationService pathService;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");

        String pi = req.getPathInfo();
        Matcher m = PAGE_PATH.matcher(pi);
        if (m.matches()) {
            Long pageId = Long.parseLong(m.group(1));
            String path = m.group(3);
            Object obj = pathService.evaluate(pageId, path);
            try (PrintWriter pw = resp.getWriter()){
                pw.write(obj.toString());
            }
            return;
        }

        m = LABEL_PATH.matcher(pi);
        if (m.matches()) {
            String label = m.group(1);
            String path = m.group(3);
            Object obj = pathService.evaluate(label, path);
            try (PrintWriter pw = resp.getWriter()){
                pw.write(obj.toString());
            }
            return;
        }

        Object obj = pathService.evaluate(pi.substring(1));
        try (PrintWriter pw = resp.getWriter()){
            pw.write(obj.toString());
        }
        //resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    public PathServlet(PathEvaluationService pathService) {
        this.pathService = pathService;
    }

    static {
        Configuration.setDefaults(new Configuration.Defaults() {
            private final GsonJsonProvider jsonProvider = new GsonJsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }
}
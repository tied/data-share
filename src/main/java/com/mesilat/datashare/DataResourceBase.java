package com.mesilat.datashare;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

public class DataResourceBase {
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    protected ArrayNode find(final Connection conn, final String q, final int limit) throws SQLException, IOException{
        ArrayNode arr = mapper.createArrayNode();

        String sql = (new StringBuilder())
            .append("SELECT C.CONTENTID, C.TITLE, D.ROOT_OBJECT\n")
            .append("FROM AO_A69A63_DATA_OBJECT D\n")
            .append("JOIN CONTENT C ON D.ID = C.CONTENTID\n")
            .append("WHERE UPPER(C.TITLE) LIKE ?\n")
            .append("ORDER BY TITLE\n")
            .append("LIMIT ")
            .append(limit)
            .toString();

        try (
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, q == null? "%": String.format("%%%s%%", q.toUpperCase()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()){
                    ObjectNode obj = mapper.createObjectNode();
                    Long id = rs.getLong("CONTENTID");
                    obj.put("id", id);
                    obj.put("title", rs.getString("TITLE"));
                    obj.put("href", String.format("%s/rest/data-share/1.0/page/%d", baseUrl, id));
                    obj.put("view", String.format("%s/pages/viewpage.action?pageId=%d", baseUrl, id));
                    obj.put("data", mapper.readTree(rs.getString("ROOT_OBJECT")));
                    arr.add(obj);
                }
            }
        }

        return arr;
    }
    protected ArrayNode find(final Connection conn, final String q, final List<String> labels, final int limit) throws SQLException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();

        String sql = (new StringBuilder())
            .append("SELECT C.CONTENTID, C.TITLE, D.ROOT_OBJECT\n")
            .append("FROM AO_A69A63_DATA_OBJECT D\n")
            .append("JOIN CONTENT C ON D.ID = C.CONTENTID\n")
            .append("JOIN CONTENT_LABEL CL ON CL.CONTENTID = C.CONTENTID\n")
            .append("JOIN LABEL L ON CL.LABELID = L.LABELID\n")
            .append("WHERE L.`NAME` IN (\n")
            .append(String.join(",", labels.stream().map(label -> String.format("'%s'", label)).collect(Collectors.toList())))
            .append(")")
            .append("AND UPPER(C.TITLE) LIKE ?\n")
            .append("ORDER BY TITLE\n")
            .append("LIMIT ")
            .append(limit)
            .toString();

        try (
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, q == null? "%": String.format("%%%s%%", q.toUpperCase()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()){
                    ObjectNode obj = mapper.createObjectNode();
                    Long id = rs.getLong("CONTENTID");
                    obj.put("id", id);
                    obj.put("title", rs.getString("TITLE"));
                    obj.put("href", String.format("%s/rest/data-share/1.0/page/%d", baseUrl, id));
                    obj.put("view", String.format("%s/pages/viewpage.action?pageId=%d", baseUrl, id));
                    obj.put("data", mapper.readTree(rs.getString("ROOT_OBJECT")));
                    arr.add(obj);
                }
            }
        }

        return arr;
    }
    protected ArrayNode find2(final Connection conn, final String q, final List<String> labels, final int limit) throws SQLException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();

        String sql = (new StringBuilder())
            .append("SELECT C.CONTENTID, C.TITLE, D.ROOT_OBJECT\n")
            .append("FROM AO_A69A63_DATA_OBJECT D\n")
            .append("JOIN CONTENT C ON D.ID = C.CONTENTID\n")
            .append("JOIN CONTENT_LABEL CL ON CL.CONTENTID = C.CONTENTID\n")
            .append("JOIN LABEL L ON CL.LABELID = L.LABELID\n")
            .append("WHERE L.`NAME` IN (\n")
            .append(String.join(",", labels.stream().map(label -> String.format("'%s'", label)).collect(Collectors.toList())))
            .append(")")
            .append("AND UPPER(D.ROOT_OBJECT) LIKE ?\n")
            .append("UNION\n")
            .append("SELECT C.CONTENTID, C.TITLE, D.ROOT_OBJECT\n")
            .append("FROM AO_A69A63_DATA_OBJECT D\n")
            .append("JOIN CONTENT C ON D.ID = C.CONTENTID\n")
            .append("JOIN CONTENT_LABEL CL ON CL.CONTENTID = C.CONTENTID\n")
            .append("JOIN LABEL L ON CL.LABELID = L.LABELID\n")
            .append("WHERE L.`NAME` IN (\n")
            .append(String.join(",", labels.stream().map(label -> String.format("'%s'", label)).collect(Collectors.toList())))
            .append(")")
            .append("AND UPPER(C.TITLE) LIKE ?\n")
            .append("ORDER BY TITLE\n")
            .append("LIMIT ")
            .append(limit)
            .toString();

        try (
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            String _q = q == null? "%": String.format("%%%s%%", q.toUpperCase());
            ps.setString(1, _q);
            ps.setString(2, _q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()){
                    ObjectNode obj = mapper.createObjectNode();
                    Long id = rs.getLong("CONTENTID");
                    obj.put("id", id);
                    obj.put("title", rs.getString("TITLE"));
                    obj.put("href", String.format("%s/rest/data-share/1.0/page/%d", baseUrl, id));
                    obj.put("view", String.format("%s/pages/viewpage.action?pageId=%d", baseUrl, id));
                    obj.put("data", mapper.readTree(rs.getString("ROOT_OBJECT")));
                    arr.add(obj);
                }
            }
        }

        return arr;
    }

    public DataResourceBase(String baseUrl){
        this.baseUrl = baseUrl;

        this.mapper.enable(SerializationConfig.Feature.INDENT_OUTPUT);
    }
}
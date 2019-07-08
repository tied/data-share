package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.ImmutableMap;
import com.mesilat.datashare.service.DataShareService;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.inject.Inject;
import javax.inject.Named;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExportAsService ({DataShareService.class})
@Named
public class DataShareServiceImpl implements DataShareService{
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    @ComponentImport
    private final ActiveObjects ao;

    @Override
    public ObjectNode getPageData(Long pageId) throws IOException {
        DataObject obj = ao.get(DataObject.class, pageId);
        if (obj == null){
            return null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            return (ObjectNode)mapper.readTree(obj.getRootObject());
        }
    }
    @Override
    public ArrayNode find(String label) throws SQLException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();

        try {
            ao.executeInTransaction(()->{
                DataObject dobj = ao.create(DataObject.class, ImmutableMap.<String,Object>builder()
                    .put("ID", DataResource.AL.decrementAndGet())
                    .build()
                );

                try (Connection conn = dobj.getEntityManager().getProvider().getConnection()) {

                    String sql = (new StringBuilder())
                        .append("SELECT C.CONTENTID, C.TITLE, D.ROOT_OBJECT\n")
                        .append("FROM AO_A69A63_DATA_OBJECT D\n")
                        .append("JOIN CONTENT C ON D.ID = C.CONTENTID\n")
                        .append("JOIN CONTENT_LABEL CL ON CL.CONTENTID = C.CONTENTID\n")
                        .append("JOIN LABEL L ON CL.LABELID = L.LABELID\n")
                        .append("WHERE L.`NAME` = ?\n")
                        .append("ORDER BY TITLE\n")
                        .toString();

                    try (
                        PreparedStatement ps = conn.prepareStatement(sql);
                    ) {
                        ps.setString(1, label);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()){
                                ObjectNode obj = (ObjectNode)mapper.readTree(rs.getString("ROOT_OBJECT"));
                                obj.put("id", rs.getLong("CONTENTID"));
                                obj.put("title", rs.getString("TITLE"));
                                arr.add(obj);
                            }
                        }
                    }
                } catch(SQLException | IOException ex){
                    throw new WrapperException(ex);
                } finally {
                    ao.delete(dobj);
                }
                return null;
            });
        } catch(WrapperException ex){
            if (ex.getCause() instanceof SQLException){
                throw (SQLException)ex.getCause();
            } else if (ex.getCause() instanceof IOException){
                throw (IOException)ex.getCause();
            }
        }

        return arr;
    }

    @Inject
    public DataShareServiceImpl(ActiveObjects ao) {
        this.ao = ao;
    }

    private static class WrapperException extends RuntimeException {
        public WrapperException(Exception ex){
            super(ex);
        }
    }
}
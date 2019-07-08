package com.mesilat.datashare.service;

import java.io.IOException;
import java.sql.SQLException;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

public interface DataShareService {
    ObjectNode getPageData(Long pageId) throws IOException;
    ArrayNode find(String label) throws SQLException, IOException;
}
package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.plugins.cql.spi.functions.CQLEvaluationContext;
import com.atlassian.confluence.plugins.cql.spi.functions.CQLMultiValueQueryFunction;
import com.mesilat.datashare.query.FilterToQuery;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.java.ao.Query;
import org.slf4j.LoggerFactory;

public class PathFilterCqlFunction extends CQLMultiValueQueryFunction {
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");
    public static final String FUNCTION_NAME = "pathDataFilter";

    private final ActiveObjects ao;

    @Override
    public int paramCount(){
        return 1;
    }

    @Override
    public Iterable<String> invoke(List<String> params, CQLEvaluationContext context) {
        if (params == null || params.size() < 1){
            return null;
        }
        FilterToQuery fq = new FilterToQuery(params.get(0));
        String query = fq.execute();
        DataAttribute[] da = ao.find(DataAttribute.class, Query.select("PAGE_ID").distinct().where(query));
        return Arrays.asList(da).stream().map(d -> d.getPageId().toString()).collect(Collectors.toList());
    }

    public PathFilterCqlFunction(ActiveObjects ao) {
        super(FUNCTION_NAME);
        this.ao = ao;
    }
}
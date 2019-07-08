package com.mesilat.datashare;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.renderer.template.TemplateRenderer;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.Map;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Scanned
public class DataSharePagesMacro implements Macro {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");
    private static final String PLUGIN_KEY = "com.mesilat.data-share";

    @ComponentImport
    private final TemplateRenderer renderer;

    @Override
    public String execute(Map<String, String> map, String string, ConversionContext cc) throws MacroExecutionException {
        try {
            if (!map.containsKey("cql")){
                map.put("cql", String.format("space = %s", cc.getSpaceKey()));
            }
            if (!map.containsKey("columns")){
                map.put("columns", "page");
            }
            if (!map.containsKey("titles")){
                map.put("titles", "Page");
            }
            return renderFromSoy("macro-resources", "Mesilat.DataShare.Templates.pagesReport.soy", map);
        } catch(Throwable ex) {
            throw new MacroExecutionException(ex);
        }
    }
    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }
    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }

    @Inject
    public DataSharePagesMacro(TemplateRenderer renderer){
        this.renderer = renderer;
    }

    public String renderFromSoy(String key, String soyTemplate, Map soyContext) {
        StringBuilder output = new StringBuilder();
        renderer.renderTo(output, String.format("%s:%s", PLUGIN_KEY, key), soyTemplate, soyContext);
        return output.toString();
    }
}
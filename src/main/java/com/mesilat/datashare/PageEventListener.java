package com.mesilat.datashare;

import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRestoreEvent;
import com.atlassian.confluence.event.events.content.page.PageTrashedEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.mesilat.datashare.service.PageProcessorService;
import javax.inject.Inject;
import javax.inject.Named;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@Named
public class PageEventListener implements InitializingBean, DisposableBean {
    @ComponentImport
    private final EventPublisher eventPublisher;
    private final PageProcessorService pageProcessorService;

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }
    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onPageCreateEvent(PageCreateEvent event) {
        pageProcessorService.processSavePage(event.getPage(), false, event.getPage().getCreator());
    }
    @EventListener
    public void onPageUpdateEvent(PageUpdateEvent event) {
        pageProcessorService.processSavePage(event.getPage(), !event.getPage().getTitle().equals(event.getOriginalPage().getTitle()), event.getPage().getLastModifier());
    }
    @EventListener
    public void pageRestoreEvent(PageRestoreEvent event) {
        pageProcessorService.processSavePage(event.getPage(), true, event.getPage().getLastModifier());
    }

    @EventListener
    public void pageTrashedEvent(PageTrashedEvent event) {
        pageProcessorService.processDeletePage(event.getPage(), event.getPage().getLastModifier());
    }
    @EventListener
    public void pageRemoveEvent(PageRemoveEvent event) {
        pageProcessorService.processDeletePage(event.getPage(), event.getPage().getLastModifier());
    }

    @Inject
    public PageEventListener(EventPublisher eventPublisher, PageProcessorService pageProcessorService){
        this.eventPublisher = eventPublisher;
        this.pageProcessorService = pageProcessorService;
    }
}
package com.mesilat.datashare;

import com.atlassian.beehive.ClusterLock;
import com.atlassian.beehive.ClusterLockService;
import com.atlassian.confluence.cluster.ClusterManager;
import com.atlassian.confluence.event.events.admin.ImportFinishedEvent;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.spring.container.ContainerManager;
import com.mesilat.datashare.service.PageProcessorService;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@Named
public class SpaceEventListener implements InitializingBean, DisposableBean {
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    @ComponentImport
    private final EventPublisher eventPublisher;
    @ComponentImport
    private final SpaceManager spaceManager;
    @ComponentImport
    private final PageManager pageManager;
    @ComponentImport
    private final ClusterManager clusterManager;
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
    public void onSpaceImportFinished(ImportFinishedEvent event) {
        if (clusterManager.isClustered()){
            ClusterLockService lockService = (ClusterLockService)ContainerManager.getComponent("clusterLockService");
            ClusterLock lock = lockService.getLockForName(SpaceEventListener.class.getName());
            if (lock.tryLock()){
                try {
                    startProcessingSpace(event.getImportContext().getSpaceKeyOfSpaceImport());                    
                } finally {
                    lock.unlock();
                }
            } else {
                LOGGER.trace("Space processing is running on another node");
            }
        } else {
            startProcessingSpace(event.getImportContext().getSpaceKeyOfSpaceImport());                    
        }
    }

    private void startProcessingSpace(String spaceKey){
        Space space = spaceManager.getSpace(spaceKey);
        if (space == null){
            LOGGER.error(String.format("Space %s could not be found", spaceKey));
        } else {
            LOGGER.info(String.format("Starting processing all space pages for space: %s", spaceKey));
            pageProcessorService.processSavePages(pageManager.getPages(space, true), null);
        }
    }

    @Inject
    public SpaceEventListener(
        EventPublisher eventPublisher,
        SpaceManager spaceManager,
        PageManager pageManager,
        ClusterManager clusterManager,
        PageProcessorService pageProcessorService
    ){
        this.eventPublisher = eventPublisher;
        this.spaceManager = spaceManager;
        this.pageManager = pageManager;
        this.clusterManager = clusterManager;
        this.pageProcessorService = pageProcessorService;
    }
}
package com.mesilat.datashare;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.beehive.ClusterLock;
import com.atlassian.beehive.ClusterLockService;
import com.atlassian.confluence.cluster.ClusterManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.spring.container.ContainerManager;
import com.mesilat.datashare.service.PageProcessingTask;
import com.mesilat.datashare.service.PageProcessorService;
import com.mesilat.jmx.MBeanName;
import com.mesilat.jmx.SimpleMBean;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.management.NotCompliantMBeanException;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@ExportAsService ({PageProcessorService.class})
@Named
@MBeanName("Confluence:name=DataSharePlugin,type=PageProcessorService")
public class PageProcessorServiceImpl extends SimpleMBean
    implements PageProcessorService, InitializingBean, DisposableBean, Runnable, PageProcessorServiceMBean {

    public static final String ACTION_SAVE      = "SAVE";
    public static final String ACTION_DELETE    = "DELE";
    public static final String STATUS_PENDING   = "PEND";
    public static final String STATUS_PROCESSED = "PROC";
    public static final String STATUS_SUCCESS   = "SUCC";
    public static final String STATUS_WARNING   = "WARN";
    public static final String STATUS_ERRORS    = "ERRS";

    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.data-share");

    @ComponentImport
    private final ActiveObjects ao;
    @ComponentImport
    private final EventPublisher eventPublisher;
    @ComponentImport
    private final UserManager userManager;
    @ComponentImport
    private final PageManager pageManager;
    @ComponentImport
    private final ClusterManager clusterManager;

    private Thread thread;

    @Override
    public void afterPropertiesSet() throws Exception {
        registerMBean();
        this.thread = new Thread(this);
        this.thread.start();
    }
    @Override
    public void destroy() throws Exception {
        unregisterMBean();
        if (this.thread != null){
            this.thread.interrupt();
            this.thread = null;
        }
    }
    @Override
    public void processSavePage(Page page, boolean pageRenamed, ConfluenceUser user) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) { /* Give Confluence some time to complete page save */ }

            try {
                //if (clusterManager.isClustered()){
                //    enqueuForSavePageProcessingInCluster(page, pageRenamed, user);
                //} else {
                    enqueuForSavePageProcessing(page, pageRenamed, user);
                //}
            } catch (Throwable ex) {
                LOGGER.error(String.format("Failed to enqueue page for save %d", page.getId()), ex);
            }
        });
        t.start();
    }
    @Override
    public void processSavePages(List<Page> pages, ConfluenceUser user) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) { /* Give Confluence some time to complete page save */ }

            try {
                //if (clusterManager.isClustered()){
                //    enqueuForSavePageProcessingInCluster(page, pageRenamed, user);
                //} else {
                pages.forEach(page -> {
                    enqueuForSavePageProcessing(page, true, user);
                });
                //}
            } catch (Throwable ex) {
                LOGGER.error("Failed to enqueue pages for save", ex);
            }
        });
        t.start();
    }
    @Override
    public void processDeletePage(Page page, ConfluenceUser user) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) { /* Give Confluence some time to complete page delete */ }

            try {
                //if (clusterManager.isClustered()){
                //    enqueueForDeletePageProcessingInCluster(page, user);
                //} else {
                    enqueueForDeletePageProcessing(page, user);
                //}
            } catch (Throwable ex) {
                LOGGER.error(String.format("Failed to enqueue page for delete %d", page.getId()), ex);
            }
        });
        t.start();
    }
    private void enqueuForSavePageProcessing(Page page, final boolean pageRenamed, ConfluenceUser user){
        ao.executeInTransaction(() -> {
            ao.create(DataProcessorQueue.class,
                new DBParam("PAGE_ID", page.getId()),
                new DBParam("VERSION", page.getVersion()),
                new DBParam("ACTION",  ACTION_SAVE),
                new DBParam("STATUS",  STATUS_PENDING),
                new DBParam("PAGE_RENAMED", pageRenamed),
                new DBParam("USER_KEY", user == null? null: user.getKey().getStringValue()),
                new DBParam("ENQUEUE_DATE", new Date())
            );
            return null;
        });
    }
/*
    private void enqueuForSavePageProcessingInCluster(Page page, final boolean pageRenamed, ConfluenceUser user) throws InterruptedException{
        ClusterLockService lockService = (ClusterLockService)ContainerManager.getComponent("clusterLockService");
        ClusterLock lock = lockService.getLockForName(String.format("%s:%d", PageProcessorServiceImpl.class.getName(), page.getId()));
        if (lock.tryLock(1000, TimeUnit.SECONDS)){
            try {
                enqueuForSavePageProcessing(page, pageRenamed, user);               
            } finally {
                lock.unlock();
            }
        } else {
            throw new RuntimeException(String.format("Failed to aquire cluster lock for page", page.getId()));
        }
    }
*/
    private void enqueueForDeletePageProcessing(Page page, ConfluenceUser user){
        ao.executeInTransaction(() -> {
            ao.create(DataProcessorQueue.class,
                new DBParam("PAGE_ID", page.getId()),
                new DBParam("VERSION", page.getVersion()),
                new DBParam("ACTION",  ACTION_DELETE),
                new DBParam("STATUS",  STATUS_PENDING),
                new DBParam("PAGE_RENAMED", false),
                new DBParam("USER_KEY", user == null? null: user.getKey().getStringValue()),
                new DBParam("ENQUEUE_DATE", new Date())
            );
            return null;
        });
    }
/*
    private void enqueueForDeletePageProcessingInCluster(Page page, ConfluenceUser user) throws InterruptedException{
        ClusterLockService lockService = (ClusterLockService)ContainerManager.getComponent("clusterLockService");
        ClusterLock lock = lockService.getLockForName(String.format("%s:%d", PageProcessorServiceImpl.class.getName(), page.getId()));
        if (lock.tryLock(1000, TimeUnit.SECONDS)){
            try {
                enqueueForDeletePageProcessing(page, user);               
            } finally {
                lock.unlock();
            }
        } else {
            throw new RuntimeException(String.format("Failed to aquire cluster lock for page", page.getId()));
        }
    }
*/


    private void run(DataProcessorQueue q){
        MyTask task = new MyTask(q.getId());
        try {
            PageProcessor pageProcessor = new PageProcessor(ao, eventPublisher, userManager, pageManager);
            switch (q.getAction()){
                case ACTION_SAVE:
                    pageProcessor.savePage(q.getPageId(), q.getVersion(), q.isPageRenamed(), task);
                    break;
                case ACTION_DELETE:
                    pageProcessor.deletePage(q.getPageId(), q.getVersion(), task);
                    break;
            }
            task.addNotification(PageProcessingTask.Status.OK, null);
        } catch(Throwable ex) {
            LOGGER.error("Task processing failed " + task.toString(), ex);
            task.addNotification(PageProcessingTask.Status.ERROR, ex.getMessage());
        } finally {
            // Record processing result
            if (task.getStatus() == PageProcessingTask.Status.INCOMPLETE){
                task.addNotification(PageProcessingTask.Status.ERROR, "Task processing finished but task status is INCOMPLETE. This should not happen");
            }
            switch (task.getStatus()){
                case OK:
                    q.setStatus(STATUS_SUCCESS);
                    if (!task.getNotifications().isEmpty()){
                        q.setLog(String.join("\n", task.getNotifications()));
                    }
                    q.save();
                    break;
                case WARNING:
                    q.setStatus(STATUS_WARNING);
                    if (!task.getNotifications().isEmpty()){
                        q.setLog(String.join("\n", task.getNotifications()));
                    }
                    q.save();
                    break;
                case ERROR:
                    q.setStatus(STATUS_ERRORS);
                    if (!task.getNotifications().isEmpty()){
                        q.setLog(String.join("\n", task.getNotifications()));
                    }
                    q.save();
            }
        }
    }

    @Override
    @SuppressWarnings("SleepWhileInLoop")
    public void run() {
        while (true){
            try {
                Long qid = clusterManager.isClustered()? aquireTaskClustered(): aquireTask();
                if (qid == null){
                    Thread.sleep(10);
                    continue;
                }

                ao.executeInTransaction(() -> {
                    DataProcessorQueue q = ao.get(DataProcessorQueue.class, qid);
                    try {
                        if (clusterManager.isClustered()){
                            String lockName = String.format("%s:%d", PageProcessorServiceImpl.class.getName(), q.getPageId());
                            ClusterLockService lockService = (ClusterLockService)ContainerManager.getComponent("clusterLockService");
                            ClusterLock lock = lockService.getLockForName(lockName);
                            if (lock.tryLock(10, TimeUnit.SECONDS)){
                                try {
                                    run(q);
                                } finally {
                                    lock.unlock();
                                }
                            } else {
                                throw new RuntimeException("Failed to aquire cluster lock for page processing");
                            }
                        } else {
                            run(q);
                        }
                    } catch (Throwable ex) {
                        LOGGER.error(String.format("Failed processing of page %d", q.getId()), ex);
                        q.setStatus(STATUS_ERRORS);
                        q.setLog(ex.getMessage());
                        q.save();
                    }

                    return null;
                });
            } catch (InterruptedException ex) {
                break;
            } catch (Throwable ex) {
                LOGGER.error("Unexpected error", ex);
            }
        }
    }
    private Long aquireTask(){
        synchronized(this){
            return ao.executeInTransaction(() -> {
                DataProcessorQueue[] q = ao.find(DataProcessorQueue.class,
                    Query
                        .select()
                        .where("STATUS = ?", STATUS_PENDING)
                        .order("ID")
                        .limit(1)
                );
                if (q.length == 0){
                    return null;
                } else {
                    q[0].setStatus(STATUS_PROCESSED);
                    q[0].save();
                    return q[0].getId();
                }
            });
        }
    }
    private Long aquireTaskClustered() throws InterruptedException {
        ClusterLockService lockService = (ClusterLockService)ContainerManager.getComponent("clusterLockService");
        ClusterLock lock = lockService.getLockForName(PageProcessorServiceImpl.class.getName());
        if (lock.tryLock(10, TimeUnit.SECONDS)){
            try {
                return aquireTask();               
            } finally {
                lock.unlock();
            }
        } else {
            throw new RuntimeException("Failed to aquire cluster lock");
        }
    }
    

    @Override
    public int getPendingCount() {
        try {
            return ao.executeInTransaction(() -> ao.count(DataProcessorQueue.class, "STATUS = ?", STATUS_PENDING));
        } catch(Throwable ex){
            LOGGER.debug("Unexpected error", ex);
            return -1;
        }
    }
    @Override
    public int getErrorCount() {
        try {
            return ao.executeInTransaction(() -> ao.count(DataProcessorQueue.class, "STATUS = ? and ENQUEUE_DATE > ?", STATUS_ERRORS, new Date(System.currentTimeMillis() - 86400000l)));
        } catch(Throwable ex){
            LOGGER.debug("Unexpected error", ex);
            return -1;
        }
    }
    @Override
    public int getWarningCount() {
        try {
            return ao.executeInTransaction(() -> ao.count(DataProcessorQueue.class, "STATUS = ? and ENQUEUE_DATE > ?", STATUS_WARNING, new Date(System.currentTimeMillis() - 86400000l)));
        } catch(Throwable ex){
            LOGGER.debug("Unexpected error", ex);
            return -1;
        }
    }
    @Override
    public boolean isClustered() {
        return clusterManager.isClustered();
    }

    @Inject
    public PageProcessorServiceImpl(
        ActiveObjects ao,
        EventPublisher eventPublisher,
        UserManager userManager,
        PageManager pageManager,
        ClusterManager clusterManager
    ) throws NotCompliantMBeanException{
        super(PageProcessorServiceMBean.class);
        this.ao = ao;
        this.eventPublisher = eventPublisher;
        this.userManager = userManager;
        this.pageManager = pageManager;
        this.clusterManager = clusterManager;
    }

    private static class MyTask implements PageProcessingTask {
        private final Long id;
        private final List<String> notifications = new ArrayList<>();
        private Status status = Status.OK;

        @Override
        public void addNotification(Status status, String notification){
            if (this.getStatus().ordinal() < status.ordinal()){
                this.status = status;
            }
            if (notification != null){
                this.getNotifications().add(notification);
            }
        }
        public Long getId() {
            return id;
        }
        public List<String> getNotifications() {
            return notifications;
        }
        public Status getStatus() {
            return status;
        }

        public MyTask(Long id){
            this.id = id;
        }
    }
}
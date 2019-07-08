package com.mesilat.datashare.service;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.user.ConfluenceUser;
import java.util.List;

public interface PageProcessorService {
    void processSavePage(Page page, boolean pageRenamed, ConfluenceUser user);
    void processSavePages(List<Page> pages, ConfluenceUser user);
    void processDeletePage(Page page, ConfluenceUser user);
}
package searchengine.services;

import org.jsoup.nodes.Document;
import searchengine.dto.messages.ResponseMessage;
import searchengine.models.Site;

public interface IndexingService {
    void indexing();
    void stopIndexing();
    public void savePage(Site site, String url, Document document);
    public void updateSiteStatusTime(Site site);
    boolean isStopIndexing();
}

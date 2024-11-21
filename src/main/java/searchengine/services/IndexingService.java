package searchengine.services;

import org.jsoup.nodes.Document;
import searchengine.dto.messages.ResponseMessage;
import searchengine.models.Site;

public interface IndexingService {
    void indexing();
}

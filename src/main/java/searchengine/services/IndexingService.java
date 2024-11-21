package searchengine.services;

public interface IndexingService {
    void indexing();
    void stopIndexing();
    boolean indexingIsShutdown();
}

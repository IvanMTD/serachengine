package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.models.Page;
import searchengine.models.Site;

import java.util.List;

public interface PageRepository extends CrudRepository<Page,Long> {
    List<Page> findAllBySiteId(long id);
    void deleteAllBySite(Site site);
    boolean existsBySiteAndPath(Site site, String url);
}

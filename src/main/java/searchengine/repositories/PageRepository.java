package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.models.Index;
import searchengine.models.Page;
import searchengine.models.Site;

import java.util.List;
import java.util.Set;

public interface PageRepository extends CrudRepository<Page,Long> {
    List<Page> findAllBySiteId(long id);
    void deleteAllBySite(Site site);
    boolean existsBySiteAndPath(Site site, String url);
    Page findBySiteAndPath(Site site, String path);

    long countBySiteId(long id);

    List<Page> findAllBy();

    List<Page> findAllByIdIn(Set<Long> indices);
}

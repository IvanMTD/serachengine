package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.config.SiteStruct;
import searchengine.models.Site;

import java.util.List;

public interface SiteRepository extends CrudRepository<Site,Long> {
    Site findByUrl(String siteUrl);

    List<Site> findAllBy();

    boolean existsByUrl(String url);
}

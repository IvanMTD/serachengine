package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.models.Site;

public interface SiteRepository extends CrudRepository<Site,Long> {
    Site findByUrl(String siteUrl);
}

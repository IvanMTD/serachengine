package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.models.Lemma;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends CrudRepository<Lemma,Long> {
    List<Lemma> findAllBySiteId(long siteId);
    Optional<Lemma> findByLemmaAndSiteId(String lemma, long id);
}

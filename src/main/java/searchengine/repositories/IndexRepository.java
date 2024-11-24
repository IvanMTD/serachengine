package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.models.Index;
import searchengine.models.Page;

import java.util.List;
import java.util.Set;

public interface IndexRepository extends CrudRepository<Index,Long> {
    List<Index> findAllByPageId(long pageId);
    boolean existsByPageAndLemma(Page page, String lemma);
    Index findByPageIdAndLemmaId(long id, long id1);

    List<Index> findAllByLemmaId(long id);
}

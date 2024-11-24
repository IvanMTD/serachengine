package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.models.Page;
import searchengine.models.Site;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Service
@Transactional
public class DeleteService {
    private final EntityManager entityManager;

    public DeleteService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void deleteSite(Site site) {
        long siteId = site.getId();

        // Удаляем записи из таблицы `index`
        entityManager.createNativeQuery("DELETE FROM `index` WHERE page_id IN (SELECT id FROM page WHERE site_id = :siteId)")
                .setParameter("siteId", siteId)
                .executeUpdate();

        // Удаляем записи из таблицы `lemma`
        entityManager.createNativeQuery("DELETE FROM lemma WHERE site_id = :siteId")
                .setParameter("siteId", siteId)
                .executeUpdate();

        // Удаляем записи из таблицы `page`
        entityManager.createNativeQuery("DELETE FROM page WHERE site_id = :siteId")
                .setParameter("siteId", siteId)
                .executeUpdate();

        // Удаляем запись из таблицы `site`
        entityManager.createNativeQuery("DELETE FROM site WHERE id = :siteId")
                .setParameter("siteId", siteId)
                .executeUpdate();
    }
}

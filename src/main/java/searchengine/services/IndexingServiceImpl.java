package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SiteStruct;
import searchengine.config.SitesList;
import searchengine.enums.IndexStatus;
import searchengine.models.Page;
import searchengine.models.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.PageIndexingTask;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private volatile boolean stopIndexing = false;

    @Override
    public void savePage(Site site, String url, Document document) {
        if (!pageRepository.existsBySiteAndPath(site, url)) {
            Page page = new Page();
            page.setSite(site);
            page.setPath(url);
            page.setCode(200);
            page.setContent(document.html());
            pageRepository.save(page);
        }
    }

    @Override
    public void updateSiteStatusTime(Site site) {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    @Override
    @Transactional
    public void indexing() {
        for (SiteStruct siteStruct : sites.getSites()) {
            try {
                // Удаляем данные по сайту
                deleteSiteData(siteStruct.getUrl());

                // Создаем новую запись о сайте
                Site site = createSiteData(siteStruct);

                // Запускаем обход страниц
                forkJoinPool.invoke(new PageIndexingTask(site, site.getUrl(), this, new HashSet<>()));

                // По завершении индексации меняем статус
                site.setStatus(IndexStatus.INDEXED);
                siteRepository.save(site);
            } catch (Exception e) {
                handleError(siteStruct, e);
            }
        }
    }

    @Override
    @Transactional
    public void stopIndexing() {
        stopIndexing = true;
        forkJoinPool.shutdownNow();
        for (SiteStruct siteStruct : sites.getSites()) {
            Site site = siteRepository.findByUrl(siteStruct.getUrl());
            if (site != null && site.getStatus() == IndexStatus.INDEXING) {
                site.setStatus(IndexStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
        }
    }

    @Transactional
    void deleteSiteData(String siteUrl) {
        Site existingSite = siteRepository.findByUrl(siteUrl);
        if (existingSite != null) {
            pageRepository.deleteAllBySite(existingSite);
            siteRepository.delete(existingSite);
        }
    }

    @Transactional
    Site createSiteData(SiteStruct siteStruct) {
        Site site = new Site();
        site.setName(siteStruct.getName());
        site.setUrl(siteStruct.getUrl());
        site.setStatus(IndexStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        return siteRepository.save(site);
    }

    @Transactional
    void handleError(SiteStruct siteStruct, Exception e) {
        Site site = siteRepository.findByUrl(siteStruct.getUrl());
        if (site != null) {
            site.setStatus(IndexStatus.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    public boolean isStopIndexing() {
        return stopIndexing;
    }
}




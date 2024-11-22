package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import searchengine.config.SiteStruct;
import searchengine.config.SitesList;
import searchengine.enums.IndexStatus;
import searchengine.models.Page;
import searchengine.models.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Service
@RequiredArgsConstructor
@PropertySource("classpath:application.yaml")
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Value("${user-agent}")
    private String userAgent;

    @Value("${referrer}")
    private String referrer;

    @Value("${connection.timeout:30000}")
    private int connectionTimeout;

    @Value("${read.timeout:30000}")
    private int readTimeout;
    private static final AtomicBoolean stopFlag = new AtomicBoolean(false);
    private ForkJoinPool forkJoinPool = null;

    @Override
    public void indexing() {
        stopFlag.set(false);
        forkJoinPool = new ForkJoinPool();
        forkJoinPool.invoke(new IndexingTask());
    }

    @Override
    public boolean indexingIsShutdown() {
        boolean check;
        if(forkJoinPool == null){
            check = true;
        }else{
            check = false;
        }
        log.info("СОСОТОЯНИЕ FORK JOIN: {}", check);
        return check;
    }

    @Override
    public void stopIndexing(){
        forkJoinPool = null;
        stopFlag.set(true);
    }

    private void deleteIndexation(Site site) {
        List<Page> pages = pageRepository.findAllBySiteId(site.getId());
        for (Page page : pages) {
            pageRepository.delete(page);
        }
        siteRepository.delete(site);
    }

    private class IndexingTask extends RecursiveAction {
        @Override
        protected void compute() {
            Map<Long,PageIndexingTask> tasks = new HashMap<>();
            for(SiteStruct siteStruct : sites.getSites()) {
                Site site = siteRepository.findByUrl(siteStruct.getUrl());
                if (site != null) {
                    deleteIndexation(site);
                }
                site = new Site();
                site.setUrl(siteStruct.getUrl());
                site.setName(siteStruct.getName());
                site.setStatus(IndexStatus.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                site = siteRepository.save(site);

                try {
                    // Запуск рекурсивной индексации страниц
                    PageIndexingTask task = new PageIndexingTask(site, "/");
                    task.fork();
                    tasks.put(site.getId(),task);
                } catch (Exception e) {
                    String message = "Indexing failed for site: " + site.getUrl() + e.getMessage();
                    site.setLastError(message);
                    site.setStatus(IndexStatus.FAILED);
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                    log.error(message);
                }
            }

            boolean done = false;
            while (!done){
                List<Boolean> doneList = new ArrayList<>();
                for(Long key : tasks.keySet()){
                    Optional<Site> optionalSite = siteRepository.findById(key);
                    if(optionalSite.isPresent()){
                        Site site = optionalSite.get();
                        PageIndexingTask task = tasks.get(key);
                        doneList.add(task.isDone());
                        if(task.isDone()){
                            site.setStatus(IndexStatus.INDEXED);
                            site.setStatusTime(LocalDateTime.now());
                            siteRepository.save(site);
                        }
                    }
                }
                int doneCount = tasks.size();
                for (Boolean taskDone : doneList){
                    if(taskDone) {
                        doneCount--;
                    }
                }
                if(doneCount == 0){
                    done = true;
                    stopFlag.set(true);
                }
            }
            log.info("ИНДЕКСИНГ ЗАВЕРШЕН!");
        }
    }

    private class PageIndexingTask extends RecursiveAction {
        private final Site site;
        private final String path;

        public PageIndexingTask(Site site, String path) {
            this.site = site;
            this.path = path;
        }

        @Override
        protected void compute() {
            if(stopFlag.get()){
                site.setStatus(IndexStatus.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Индексация остановлена пользователем!");
                this.cancel(true);
            }
            try {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                indexPage(site, path);
            } catch (IOException e) {
                site.setLastError(e.getMessage());
                site.setStatus(IndexStatus.FAILED);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                throw new RuntimeException(e);
            }
        }

        private void indexPage(Site site, String path) throws IOException {
            Page page = new Page();
            page.setPath(path);
            page.setSite(site);

            Connection.Response response = null;
            int retries = 3;
            for (int i = 0; i < retries; i++) {
                try {
                    response = Jsoup.connect(site.getUrl() + path)
                            .userAgent(userAgent)
                            .referrer(referrer)
                            .timeout(connectionTimeout)
                            .maxBodySize(0)
                            .ignoreContentType(true)
                            .execute();
                    break;
                } catch (SocketTimeoutException e) {
                    if (i < retries - 1) {
                        log.warn("Retry {}: SocketTimeoutException for URL: {}", i + 1, site.getUrl() + path);
                    } else {
                        throw e;
                    }
                } catch (HttpStatusException e) {
                    if (e.getStatusCode() == 404) {
                        page.setCode(e.getStatusCode());
                        page.setContent("Page not found");
                        pageRepository.save(page);
                        log.info("save page with 404 status: {}", page.getPath());
                        return;
                    } else {
                        throw e;
                    }
                }
            }

            if (response != null) {
                String contentType = response.contentType();
                if (Objects.requireNonNull(contentType).startsWith("text/")) {
                    page.setCode(response.statusCode());
                    page.setContent(response.body());
                    pageRepository.save(page);
                    log.info("save page: {}", page.getPath());

                    Document document = response.parse();
                    Elements elements = document.select("a[href]");
                    for (Element element : elements) {
                        String link = element.absUrl("href");
                        if (link.startsWith(site.getUrl())) {
                            String relativePath = link.substring(site.getUrl().length());
                            if (pageRepository.findBySiteAndPath(site, relativePath) == null) {
                                ForkJoinPool joinPool = new ForkJoinPool();
                                joinPool.invoke(new PageIndexingTask(site, relativePath));
                                //indexPage(site,relativePath);
                            }
                        }
                    }
                }
            }
        }
    }
}


/*@Slf4j
@Service
@RequiredArgsConstructor
@PropertySource("classpath:application.yaml")
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Value("${user-agent}")
    private String userAgent;

    @Value("${referrer}")
    private String referrer;

    @Value("${connection.timeout:30000}")
    private int connectionTimeout;

    @Value("${read.timeout:30000}")
    private int readTimeout;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool(); // Один пул на весь процесс

    @Override
    public void indexing() {
        List<RecursiveAction> tasks = new ArrayList<>();
        for (SiteStruct siteStruct : sites.getSites()) {
            tasks.add(new IndexingTask(siteStruct));
        }

        for (RecursiveAction task : tasks) {
            forkJoinPool.invoke(task);
        }
    }

    private void deleteIndexation(Site site) {
        List<Page> pages = pageRepository.findAllBySiteId(site.getId());
        for (Page page : pages) {
            pageRepository.delete(page);
        }
        siteRepository.delete(site);
    }

    private class IndexingTask extends RecursiveAction {
        private final SiteStruct siteStruct;

        public IndexingTask(SiteStruct siteStruct) {
            this.siteStruct = siteStruct;
        }

        @Override
        protected void compute() {
            Site site = siteRepository.findByUrl(siteStruct.getUrl());
            if (site != null) {
                log.info("site is not null! delete!");
                deleteIndexation(site);
            }
            site = new Site();
            site.setUrl(siteStruct.getUrl());
            site.setName(siteStruct.getName());
            site.setStatus(IndexStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site = siteRepository.save(site);

            // Запуск рекурсивной индексации страниц
            new PageIndexingTask(site, "/").fork(); // Подзадача
        }
    }

    private class PageIndexingTask extends RecursiveAction {
        private final Site site;
        private final String path;

        public PageIndexingTask(Site site, String path) {
            this.site = site;
            this.path = path;
        }

        @Override
        protected void compute() {
            try {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                indexPage(site, path);
            } catch (IOException e) {
                site.setLastError(e.getMessage());
                site.setStatus(IndexStatus.FAILED);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                throw new RuntimeException(e);
            }
        }

        private void indexPage(Site site, String path) throws IOException {
            log.info("path incoming: {}", path);
            Page page = new Page();
            page.setPath(path);
            page.setSite(site);

            Connection.Response response = null;
            int retries = 3;
            for (int i = 0; i < retries; i++) {
                try {
                    response = Jsoup.connect(site.getUrl() + path)
                            .userAgent(userAgent)
                            .referrer(referrer)
                            .timeout(connectionTimeout)
                            .maxBodySize(0)
                            .ignoreContentType(true)
                            .execute();
                    break;
                } catch (SocketTimeoutException e) {
                    if (i < retries - 1) {
                        log.warn("Retry {}: SocketTimeoutException for URL: {}", i + 1, site.getUrl() + path);
                    } else {
                        throw e;
                    }
                } catch (HttpStatusException e) {
                    if (e.getStatusCode() == 404) {
                        page.setCode(e.getStatusCode());
                        page.setContent("Page not found");
                        pageRepository.save(page);
                        log.info("save page with 404 status: {}", page.getPath());
                        return;
                    } else {
                        throw e;
                    }
                }
            }

            if (response != null) {
                String contentType = response.contentType();
                if(contentType.startsWith("text/")) {
                    page.setCode(response.statusCode());
                    page.setContent(response.body());
                    pageRepository.save(page);
                    log.info("save page: {}", page.getPath());

                    Document document = response.parse();
                    Elements elements = document.select("a[href]");
                    for (Element element : elements) {
                        String link = element.absUrl("href");
                        if (link.startsWith(site.getUrl())) {
                            log.info("found path: {}", link);
                            String relativePath = link.substring(site.getUrl().length());
                            if (pageRepository.findBySiteAndPath(site, relativePath) == null) {
                                ForkJoinPool joinPool = new ForkJoinPool();
                                joinPool.invoke(new PageIndexingTask(site, relativePath));
                            }
                        }
                    }
                }
            }
        }
    }
}*/





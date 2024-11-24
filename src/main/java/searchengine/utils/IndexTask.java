package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.enums.IndexStatus;
import searchengine.models.Index;
import searchengine.models.Lemma;
import searchengine.models.Page;
import searchengine.models.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class IndexTask extends RecursiveAction { private static final AtomicInteger activeTaskCounter = new AtomicInteger(0);
    private final String userAgent = "HeliontSearchBot";
    private final String referrer = "http://www.google.com";
    private final int connectionTimeout = 10000;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private Site site;
    private final List<String> urls;

    public IndexTask(SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository, Site site, List<String> urls) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.site = site;
        this.urls = urls;
    }

    @Override
    protected synchronized void compute() {
        if(activeTaskCounter.get() >= 0) {
            activeTaskCounter.incrementAndGet();
            try {
                if (urls.size() < 30) {
                    for(String url : urls) {
                        Page page = pageRepository.findBySiteAndPath(site, url);
                        if (page == null) {
                            page = new Page();
                            page.setPath(url);
                            page.setSite(site);
                            try {
                                site.setStatus(IndexStatus.INDEXING);
                                site.setStatusTime(LocalDateTime.now());
                                this.site = siteRepository.save(site);

                                // Create a trust manager that does not validate certificate chains
                                TrustManager[] trustAllCerts = new TrustManager[]{
                                        new X509TrustManager() {
                                            public X509Certificate[] getAcceptedIssuers() {
                                                return null;
                                            }

                                            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                                            }

                                            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                                            }
                                        }
                                };

                                // Install the all-trusting trust manager
                                SSLContext sc = SSLContext.getInstance("TLS");
                                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

                                // Create all-trusting host name verifier
                                HostnameVerifier allHostsValid = (hostname, session) -> true;
                                HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

                                Connection.Response response = Jsoup.connect(site.getUrl() + url)
                                        .userAgent(userAgent)
                                        .referrer(referrer)
                                        .timeout(connectionTimeout)
                                        .maxBodySize(0)
                                        .ignoreContentType(true)
                                        .execute();

                                String contentType = response.contentType();
                                if (contentType != null) {
                                    if (contentType.startsWith("text")) {
                                        page.setCode(response.statusCode());
                                        page.setContent(response.body());
                                        if(pageRepository.findBySiteAndPath(site,url) == null) {
                                            page = pageRepository.save(page);
                                            uploadLemmas(page);
                                            Set<String> urls = new HashSet<>();
                                            Document document = response.parse();
                                            Elements elements = document.select("a[href]");
                                            for (Element element : elements) {
                                                String link = element.absUrl("href");
                                                if (link.startsWith(site.getUrl())) {
                                                    String relativePath = link.substring(site.getUrl().length());
                                                    urls.add(relativePath);
                                                }
                                            }
                                            IndexTask task = new IndexTask(siteRepository, pageRepository, lemmaRepository, indexRepository, site, new ArrayList<>(urls));
                                            ForkJoinPool.commonPool().invoke(task);
                                        }
                                    }
                                }
                            } catch (HttpStatusException | SocketTimeoutException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                log.error("IO Exception: {}", e.getMessage());
                                site.setStatus(IndexStatus.FAILED);
                                site.setStatusTime(LocalDateTime.now());
                                site.setLastError("Ошибка: " + e.getMessage());
                                site = siteRepository.save(site);
                                throw new RuntimeException(e);
                            } catch (Exception e) {
                                if(!e.getMessage().startsWith("query did not")) {
                                    log.error("Unexpected Exception: {}", e.getMessage());
                                    site.setStatus(IndexStatus.FAILED);
                                    site.setStatusTime(LocalDateTime.now());
                                    site.setLastError("Ошибка: " + e.getMessage());
                                    site = siteRepository.save(site);
                                    throw new RuntimeException(e);
                                }else{
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }else{
                    List<String> left = new ArrayList<>();
                    List<String> right = new ArrayList<>();
                    for(int i=0; i<urls.size(); i++){
                        if(i < urls.size() / 2){
                            left.add(urls.get(i));
                        }else{
                            right.add(urls.get(i));
                        }
                    }
                    IndexTask leftTask = new IndexTask(siteRepository,pageRepository,lemmaRepository,indexRepository,site,left);
                    leftTask.fork();
                    IndexTask rightTask = new IndexTask(siteRepository,pageRepository,lemmaRepository,indexRepository,site,right);
                    rightTask.fork();
                }
            }finally {
                if(activeTaskCounter.get() == -1){
                    if(site.getStatus().equals(IndexStatus.INDEXING)){
                        site.setStatus(IndexStatus.FAILED);
                        site.setLastError("Индексация остановлена пользователем!");
                        site = siteRepository.save(site);
                        this.cancel(true);
                    }
                }else{
                    int taskCounter = activeTaskCounter.decrementAndGet();
                    if(taskCounter == 0){
                        for(Site s : siteRepository.findAllBy()){
                            if(!s.getStatus().equals(IndexStatus.FAILED)){
                                s.setStatus(IndexStatus.INDEXED);
                                siteRepository.save(s);
                            }
                        }
                    }
                }
            }
        }else{
            if(site.getStatus().equals(IndexStatus.INDEXING)){
                site.setStatus(IndexStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем!");
                site = siteRepository.save(site);
            }
        }
    }

    public void uploadLemmas(Page page){
        Map<String,Integer> lemmas = LemmaUtil.getLemmas(page.getContent());
        for(String lemma : lemmas.keySet()){
            Optional<Lemma> optionalLemma = lemmaRepository.findByLemmaAndSiteId(lemma,site.getId());
            Lemma l;
            if(optionalLemma.isPresent()){
                l = optionalLemma.get();
                l.setFrequency(l.getFrequency() + 1);
            }else{
                l = new Lemma();
                l.setLemma(lemma);
                l.setFrequency(1);
                l.setSite(site);
            }
            Lemma saved = lemmaRepository.save(l);

            Index index = new Index();
            index.setLemma(saved);
            index.setPage(page);
            index.setRank(lemmas.get(lemma));
            indexRepository.save(index);
        }
    }

    public static int getActiveTaskCounter(){
        return activeTaskCounter.get();
    }

    public static void setActiveTaskCounter(int count){
        activeTaskCounter.set(count);
    }
}



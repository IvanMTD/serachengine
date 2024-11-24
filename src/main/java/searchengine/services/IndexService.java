package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.SiteStruct;
import searchengine.config.SitesList;
import searchengine.enums.IndexStatus;
import searchengine.models.Index;
import searchengine.models.Lemma;
import searchengine.models.Page;
import searchengine.models.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.IndexTask;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final DeleteService deleteService;

    public void start(){
        IndexTask.setActiveTaskCounter(0);
        for(SiteStruct siteStruct : sites.getSites()){
            Site site = siteRepository.findByUrl(siteStruct.getUrl());
            if(site != null){
                //deleteSite(site);
                deleteService.deleteSite(site);
            }
            site = new Site();
            site.setStatus(IndexStatus.INDEXING);
            site.setName(siteStruct.getName());
            site.setUrl(siteStruct.getUrl());
            site.setStatusTime(LocalDateTime.now());
            site.setLastError("");
            site = siteRepository.save(site);
            IndexTask task = new IndexTask(siteRepository,pageRepository,lemmaRepository,indexRepository,site, List.of(""));
            task.fork();
        }
    }

    public void start(String url, String path){
        IndexTask.setActiveTaskCounter(0);
        Site site = siteRepository.findByUrl(url);
        if(site == null){
            site = new Site();
            site.setStatus(IndexStatus.INDEXING);
            List<SiteStruct> structs = sites.getSites().stream().filter(siteStruct -> siteStruct.getUrl().equals(url)).toList();
            site.setName(structs.get(0).getName());
            site.setUrl(structs.get(0).getUrl());
            site.setStatusTime(LocalDateTime.now());
            site.setLastError("");
        }else{
            site.setStatusTime(LocalDateTime.now());
            site.setStatus(IndexStatus.INDEXING);
        }
        site = siteRepository.save(site);

        Page page = pageRepository.findBySiteAndPath(site,path);
        if(page != null){
            deleteService.deleteSite(site);
        }

        IndexTask task = new IndexTask(siteRepository,pageRepository,lemmaRepository,indexRepository,site, List.of(path));
        task.fork();
    }

    public void stop(){
        IndexTask.setActiveTaskCounter(-1);
    }

    public boolean isRunning(){
        return IndexTask.getActiveTaskCounter() > 0;
    }

    public void deleteSite(Site site){
        pageRepository.findAllBySiteId(site.getId()).forEach(this::deletePage);
        lemmaRepository.findAllBySiteId(site.getId()).forEach(this::deleteLemma);
        siteRepository.delete(site);
    }

    public void deleteAllLemmas(Site site){
        lemmaRepository.findAllBySiteId(site.getId()).forEach(this::deleteLemma);
    }

    public void deletePage(Page page){
        indexRepository.findAllByPageId(page.getId()).forEach(this::deleteIndex);
        pageRepository.delete(page);
    }
    public void deleteIndex(Index index){
        indexRepository.delete(index);
    }
    public void deleteLemma(Lemma lemma){
        lemmaRepository.delete(lemma);
    }

    public void analyze(String url, List<String> queryLemmas) {
        Site site = siteRepository.findByUrl(url);
        List<Lemma> lemmasTotal = new ArrayList<>();
        if(site != null){
            for(String queryLemma : queryLemmas){
                Optional<Lemma> optionalLemma = lemmaRepository.findByLemmaAndSiteId(queryLemma,site.getId());
                if(optionalLemma.isPresent()){
                    Lemma lemma = optionalLemma.get();
                    int pagePercent = pageRepository.findAllBySiteId(site.getId()).size() / 2;
                    if(lemma.getFrequency() < pagePercent){
                        lemmasTotal.add(lemma);
                    }
                }
            }
        }else{

        }
        lemmasTotal = lemmasTotal.stream().sorted(Comparator.comparing(Lemma::getFrequency)).collect(Collectors.toList());
        List<Page> pages = new ArrayList<>();
        for (Lemma lemma : lemmasTotal) {
            if (pages.isEmpty()) {
                // Находим все страницы, содержащие первую лемму
                List<Index> indices = indexRepository.findAllByLemmaId(lemma.getId());
                Set<Long> ids = new HashSet<>();
                for(Index index : indices){
                    ids.add(index.getPage().getId());
                }
                pages = new ArrayList<>(pageRepository.findAllByIdIn(ids));
            } else {
                // Фильтруем текущий список страниц, оставляя только те, которые содержат текущую лемму
                List<Index> indices = indexRepository.findAllByLemmaId(lemma.getId());
                Set<Long> pageIds = indices.stream()
                        .map(index -> index.getPage().getId())
                        .collect(Collectors.toSet());
                pages = pages.stream()
                        .filter(page -> pageIds.contains(page.getId()))
                        .collect(Collectors.toList());
            }
        }

        for(Page page : pages){
            System.out.println(page.getPath());
        }
    }
}

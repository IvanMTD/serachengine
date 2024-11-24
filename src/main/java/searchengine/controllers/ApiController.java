package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SiteStruct;
import searchengine.config.SitesList;
import searchengine.dto.messages.DataResponse;
import searchengine.dto.messages.ResponseMessage;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexService;
import searchengine.services.StatisticsService;
import searchengine.utils.LemmaUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final SitesList sites;
    private final StatisticsService statisticsService;
    private final IndexService indexService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ResponseMessage> startIndexing() {
        if(indexService.isRunning()){
            return ResponseEntity.ok().body(new ResponseMessage(false,"Индексация уже запущена"));
        }
        indexService.start();
        return ResponseEntity.ok().body(new ResponseMessage(true,""));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ResponseMessage> stopIndexing() {
        if(!indexService.isRunning()){
            return ResponseEntity.ok().body(new ResponseMessage(false,"Индексация не запущена"));
        }
        indexService.stop();
        return ResponseEntity.ok().body(new ResponseMessage(true,""));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ResponseMessage> indexPage(@RequestParam(name = "url") String url){
        String siteUrl = null;
        String path = null;
        for(SiteStruct siteStruct : sites.getSites()){
            String base = siteStruct.getUrl();
            if(url.startsWith(base)){
                siteUrl = base;
                path = url.substring(base.length());
            }
        }

        if(siteUrl == null){
            return ResponseEntity.ok().body(new ResponseMessage(false,"Данная страница находится за пределами сайтов, \n" +
                    "указанных в конфигурационном файле\n"));
        }else{
            indexService.start(siteUrl,path);
            return ResponseEntity.ok().body(new ResponseMessage(true,""));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<DataResponse> search(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "offset") int offset,
            @RequestParam(name = "limit") int limit
            ) {

        Map<String,Integer> lemmas = LemmaUtil.getLemmas(query);
        List<String> list = new ArrayList<>();
        for(String key : lemmas.keySet()){
            list.add(key);
        }

        indexService.analyze(site,list);

        return null;
    }
}

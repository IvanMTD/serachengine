package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.messages.ResponseMessage;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ResponseMessage> startIndexing() {
        if(!indexingService.indexingIsShutdown()){
            return ResponseEntity.ok().body(new ResponseMessage(false,"Индексация уже запущена"));
        }
        indexingService.indexing();
        return ResponseEntity.ok().body(new ResponseMessage(true,""));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ResponseMessage> stopIndexing() {
        if(indexingService.indexingIsShutdown()){
            return ResponseEntity.ok().body(new ResponseMessage(false,"Индексация не запущена"));
        }
        indexingService.stopIndexing();
        return ResponseEntity.ok().body(new ResponseMessage(true,""));
    }
}

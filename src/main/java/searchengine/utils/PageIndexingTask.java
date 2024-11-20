package searchengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.enums.IndexStatus;
import searchengine.models.Site;
import searchengine.services.IndexingService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveTask;

public class PageIndexingTask extends RecursiveTask<Void> {

    private final Site site;
    private final String url;
    private final IndexingService indexingService;
    private final Set<String> visitedUrls;

    public PageIndexingTask(Site site, String url, IndexingService indexingService, Set<String> visitedUrls) {
        this.site = site;
        this.url = url;
        this.indexingService = indexingService;
        this.visitedUrls = visitedUrls;
    }

    @Override
    protected Void compute() {
        if (indexingService.isStopIndexing()) {
            return null;
        }

        if (visitedUrls.contains(url)) {
            return null;
        }

        visitedUrls.add(url);

        try {
            // Получаем HTML-контент страницы
            Document document = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .referrer("http://www.google.com")
                    .get();

            // Сохраняем информацию о странице
            indexingService.savePage(site, url, document);

            // Ищем все ссылки на текущей странице
            List<PageIndexingTask> subtasks = new ArrayList<>();
            for (Element link : document.select("a[href]")) {
                String linkUrl = link.attr("abs:href");
                if (linkUrl.startsWith(site.getUrl()) && !visitedUrls.contains(linkUrl)) { // Оставляем только ссылки на этом сайте
                    subtasks.add(new PageIndexingTask(site, linkUrl, indexingService, visitedUrls));
                }
            }

            // Запускаем подзадачи
            invokeAll(subtasks);

            site.setStatusTime(LocalDateTime.now());
            indexingService.updateSiteStatusTime(site);

            // Задержка между запросами
            Thread.sleep(500);
        } catch (IOException | InterruptedException e) {
            // Логируем ошибку и устанавливаем статус FAILED
            site.setStatus(IndexStatus.FAILED);
            site.setLastError("Ошибка доступа к странице: " + url + " - " + e.getMessage());
            indexingService.updateSiteStatusTime(site);
            indexingService.stopIndexing();
            throw new RuntimeException(e); // Перебрасываем исключение, чтобы остановить индексацию
        }
        return null;
    }
}



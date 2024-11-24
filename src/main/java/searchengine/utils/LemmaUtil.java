package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class LemmaUtil {
    private static final LuceneMorphology luceneMorph;

    static {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public static Map<String, Integer> getLemmas(String content) {
        //long start = System.currentTimeMillis();
        Map<String, Integer> lemmas = new ConcurrentHashMap<>();
        String cleanText = extractTextFromHtml(content);
        String[] words = cleanText.split(" ");

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<Void>> futures = new ArrayList<>();

        for (String word : words) {
            Future<Void> future = executor.submit(() -> {
                List<String> baseForms = getWordBaseForm(word);
                if (baseForms != null) {
                    for (String baseForm : baseForms) {
                        lemmas.merge(baseForm, 1, Integer::sum);
                    }
                }
                return null;
            });
            futures.add(future);
        }

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();

        //long end = System.currentTimeMillis();
        //long delta = end - start;
        //System.out.println("Заняло: " + ((delta / 1000) / 60) + " минут и " + (delta / 1000) + " секунд");
        return lemmas;
    }

    public static String extractTextFromHtml(String html) {
        Document document = Jsoup.parse(html);
        String text = document.text();
        text = text.replaceAll("\\s+", " ");
        text = text.trim();
        return text;
    }

    private static List<String> getWordBaseForm(String word) {
        try {
            return cache.computeIfAbsent(word, k -> luceneMorph.getNormalForms(word));
        } catch (WrongCharaterException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }
}

/*public class LemmaUtil {
    public static Map<String,Integer> getLemmas(String content){
        long start = System.currentTimeMillis();
        Map<String,Integer> lemmas = new HashMap<>();
        String cleanText = extractTextFromHtml(content);
        String[] words = cleanText.split(" ");
        for(String word : words){
            List<String> baseForms = getWordBaseForm(word);
            if(baseForms != null) {
                for (String baseForm : baseForms) {
                    lemmas.put(baseForm, lemmas.get(baseForm) == null ? 1 : lemmas.get(baseForm) + 1);
                }
            }
        }
        long end = System.currentTimeMillis();
        long delta = end - start;
        System.out.println("Заняло: " + ((delta / 1000) / 60) + " минут и " + (delta / 1000) + " секунд");
        return lemmas;
    }
    public static String extractTextFromHtml(String html) {
        Document document = Jsoup.parse(html);
        String text = document.text();
        text = text.replaceAll("\\s+", " ");
        text = text.trim();
        return text;
    }

    private static List<String> getWordBaseForm(String word){
        try {
            LuceneMorphology luceneMorph = null;
            luceneMorph = new RussianLuceneMorphology();
            return luceneMorph.getNormalForms(word);
        } catch (WrongCharaterException | IOException e) {
            return null;
        }
    }
}*/

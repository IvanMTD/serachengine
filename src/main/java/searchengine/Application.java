package searchengine;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.List;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    /*public static void main(String[] args) {
        try {
            LuceneMorphology luceneMorph = null;
            luceneMorph = new RussianLuceneMorphology();
            List<String> wordBaseForms = luceneMorph.getNormalForms("E9");
            wordBaseForms.forEach(System.out::println);
        } catch (WrongCharaterException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/
}

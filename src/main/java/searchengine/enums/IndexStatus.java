package searchengine.enums;

public enum IndexStatus {
    INDEXING("Идёт индексация"),
    INDEXED("Проиндексирован"),
    FAILED("Ошибка");
    private final String title;
    IndexStatus(String title){
        this.title = title;
    }
    public String getTitle() {
        return title;
    }
}

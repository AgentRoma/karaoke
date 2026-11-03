package com.example.karaoke.service;

import com.example.karaoke.model.QueueItem;
import com.example.karaoke.model.Song;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class KaraokeService {
    private final Map<Long, Song> allSongsMap = new ConcurrentHashMap<>();
    private final List<QueueItem> queue = new ArrayList<>();

    private final AtomicLong songIdGenerator = new AtomicLong(1);
    private final AtomicLong queueIdGenerator = new AtomicLong(1);

    private final SongDatabaseInitializer databaseInitializer;

    public KaraokeService(SongDatabaseInitializer databaseInitializer) {
        this.databaseInitializer = databaseInitializer;
    }

    @PostConstruct
    public void initDatabase() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = new ClassPathResource("songs.json").getInputStream();
            List<Song> jsonSongs = mapper.readValue(inputStream, new TypeReference<List<Song>>() {});
            for (Song s : jsonSongs) {
                allSongsMap.put(s.getId(), s);
            }
        } catch (Exception e) {
            System.err.println("Info: Brak lub błąd odczytu opcjonalnego pliku songs.json");
        }

        List<Song> generatedCatalog = databaseInitializer.generateInitialCatalog(songIdGenerator);
        for (Song s : generatedCatalog) {
            allSongsMap.put(s.getId(), s);
        }

        System.out.println(">>> ŁĄCZNIE W BAZIE KLUBU: " + allSongsMap.size() + " PIOSENEK <<<");
    }

    public List<Song> searchSongs(String query, String source, String language) {
        String rawQuery = (query == null) ? "" : query.trim();
        String normalizedQuery = normalizeText(rawQuery);
        String transliteratedQuery = cyrillicToLatin(normalizedQuery);

        return allSongsMap.values().stream()
                // 1. Filtracja źródła (Karafan / iSing / Wszystkie bazy)
                .filter(s -> source == null || source.isEmpty() || source.equalsIgnoreCase("Wszystkie bazy") || s.getSource().equalsIgnoreCase(source))

                // 2. Filtracja języka (PL, EN, RU, UA, FR, ES / Wszystkie)
                .filter(s -> language == null || language.isEmpty() || language.equalsIgnoreCase("Wszystkie") || s.getLanguage().equalsIgnoreCase(language))

                // 3. Wyszukiwanie niewrażliwe na znaki specjalne (np. AC/DC -> acdc)
                .filter(s -> {
                    if (normalizedQuery.isEmpty()) return true;

                    String titleNorm = normalizeText(s.getTitle());
                    String artistNorm = normalizeText(s.getArtist());

                    return titleNorm.contains(normalizedQuery) || artistNorm.contains(normalizedQuery) ||
                            titleNorm.contains(transliteratedQuery) || artistNorm.contains(transliteratedQuery);
                })
                .collect(Collectors.toList());
    }

    public List<Song> searchSongs(String query, String source) {
        return searchSongs(query, source, null);
    }

    // Pomocnicza metoda usuwająca znaki specjalne, spacji i myślniki dla niezawodnego wyszukiwania
    private String normalizeText(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", "");
    }

    // Transliteracja cyrylicy na alfabet łaciński
    private String cyrillicToLatin(String text) {
        if (text == null) return "";
        Map<Character, String> map = new HashMap<>();
        map.put('а', "a"); map.put('б', "b"); map.put('в', "v"); map.put('г', "g");
        map.put('д', "d"); map.put('е', "e"); map.put('ё', "yo"); map.put('ж', "zh");
        map.put('з', "z"); map.put('и', "i"); map.put('й', "y"); map.put('к', "k");
        map.put('л', "l"); map.put('м', "m"); map.put('н', "n"); map.put('о', "o");
        map.put('п', "p"); map.put('р', "r"); map.put('с', "s"); map.put('т', "t");
        map.put('у', "u"); map.put('ф', "f"); map.put('х', "kh"); map.put('ц', "ts");
        map.put('ч', "ch"); map.put('ш', "sh"); map.put('щ', "sch"); map.put('ы', "y");
        map.put('э', "e"); map.put('ю', "yu"); map.put('я', "ya");

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(map.getOrDefault(c, String.valueOf(c)));
        }
        return sb.toString();
    }

    public QueueItem addToQueue(Long songId, String userName) {
        Song song = allSongsMap.get(songId);
        if (song == null) return null;

        QueueItem item = new QueueItem(queueIdGenerator.getAndIncrement(), userName, song);
        queue.add(item);
        return item;
    }

    public List<QueueItem> getQueue() {
        return queue.stream()
                .filter(item -> !item.getStatus().equals("FINISHED"))
                .collect(Collectors.toList());
    }

    public void updateStatus(Long queueId, String status) {
        queue.stream()
                .filter(item -> item.getId().equals(queueId))
                .findFirst()
                .ifPresent(item -> item.setStatus(status));
    }

    public void removeFromQueue(Long queueId) {
        queue.removeIf(item -> item.getId().equals(queueId));
    }
}
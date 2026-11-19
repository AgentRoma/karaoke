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
import java.util.regex.Pattern;
import java.util.Locale;
import java.util.Objects;

/**
 * Serwis zarządzający bazą piosenek i kolejką karaoke.
 * Zoptymalizowane wyszukiwanie z indeksem i poprawną obsługą diakrytyk.
 */
@Service
public class KaraokeService {
    private final Map<Long, Song> allSongsMap = new ConcurrentHashMap<>();
    private final List<QueueItem> queue = new ArrayList<>();

    private final AtomicLong songIdGenerator = new AtomicLong(1);
    private final AtomicLong queueIdGenerator = new AtomicLong(1);

    private final SongDatabaseInitializer databaseInitializer;

    // Indeksy do szybkiego wyszukiwania
    private final Map<String, Set<Long>> titleIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> artistIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> titleArtistIndex = new ConcurrentHashMap<>();

    // Wzorce do walidacji
    private static final int MAX_QUERY_LENGTH = 100;
    private static final Pattern SAFE_QUERY_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s\\-_.'(),]+$");

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

        buildSearchIndexes();

        System.out.println(">>> ŁĄCZNIE W BAZIE KLUBU: " + allSongsMap.size() + " PIOSENEK <<<");
    }

    /**
     * Buduje indeksy wyszukiwania dla O(1) lookup.
     * Normalizuje i transliteruje tytuły i wykonawców.
     */
    private void buildSearchIndexes() {
        titleIndex.clear();
        artistIndex.clear();
        titleArtistIndex.clear();

        for (Song song : allSongsMap.values()) {
            String titleNorm = normalizeAndTransliterate(song.getTitle());
            String artistNorm = normalizeAndTransliterate(song.getArtist());

            // Indeks tytułów - każde słowo i prefiksy
            indexWords(titleNorm, song.getId(), titleIndex);
            indexWords(artistNorm, song.getId(), artistIndex);

            // Indeks łączny "tytuł wykonawca"
            String combined = titleNorm + " " + artistNorm;
            indexWords(combined, song.getId(), titleArtistIndex);
        }
    }

    private void indexWords(String text, Long songId, Map<String, Set<Long>> index) {
        if (text == null || text.isEmpty()) return;

        // Podziel na słowa
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() < 2) continue;

            // Dodaj pełne słowo
            index.computeIfAbsent(word, k -> ConcurrentHashMap.newKeySet()).add(songId);

            // Dodaj prefiksy (dla autocomplete / partial match) - min 3 znaki
            for (int i = 3; i <= word.length(); i++) {
                String prefix = word.substring(0, i);
                index.computeIfAbsent(prefix, k -> ConcurrentHashMap.newKeySet()).add(songId);
            }
        }
    }

    /**
     * Główna metoda wyszukiwania - używa indeksów dla szybkości.
     * Fallback do skanowania pełnego tylko gdy indeks nie znajdzie wyników.
     */
    public List<Song> searchSongs(String query, String source, String language) {
        String rawQuery = (query == null) ? "" : query.trim();

        // Walidacja i sanitizacja query
        if (rawQuery.length() > MAX_QUERY_LENGTH) {
            rawQuery = rawQuery.substring(0, MAX_QUERY_LENGTH);
        }
        if (!SAFE_QUERY_PATTERN.matcher(rawQuery).matches()) {
            // Usuń niebezpieczne znaki zamiast odrzucać
            rawQuery = rawQuery.replaceAll("[^\\p{L}\\p{N}\\s\\-_.'(),]", "");
        }

        String normalizedQuery = normalizeAndTransliterate(rawQuery);

        // Szybkie wyszukiwanie po indeksach
        Set<Long> candidateIds = new LinkedHashSet<>();

        if (!normalizedQuery.isEmpty()) {
            String[] queryWords = normalizedQuery.split("\\s+");

            for (String word : queryWords) {
                if (word.length() < 2) continue;

                // Szukaj w indeksach (tytuł, wykonawca, łączny)
                addMatches(word, titleIndex, candidateIds);
                addMatches(word, artistIndex, candidateIds);
                addMatches(word, titleArtistIndex, candidateIds);

                // Prefix match dla ostatniego słowa (autocomplete)
                if (word.length() >= 3) {
                    addMatches(word, titleIndex, candidateIds);
                    addMatches(word, artistIndex, candidateIds);
                }
            }
        }

        // Jeśli indeks nie znalazł nic, fallback do skanowania (rzadkie)
        List<Song> candidates;
        if (candidateIds.isEmpty() && !normalizedQuery.isEmpty()) {
            candidates = allSongsMap.values().stream()
                    .filter(s -> matchesQuery(s, normalizedQuery))
                    .collect(Collectors.toList());
        } else if (candidateIds.isEmpty()) {
            candidates = new ArrayList<>(allSongsMap.values());
        } else {
            candidates = candidateIds.stream()
                    .map(allSongsMap::get)
                    .filter(Objects::nonNull)
                    .filter(s -> matchesQuery(s, normalizedQuery)) // weryfikacja dokładna
                    .collect(Collectors.toList());
        }

        // Filtracja po źródle i języku
        return candidates.stream()
                .filter(s -> source == null || source.isEmpty() || source.equalsIgnoreCase("Wszystkie bazy") || s.getSource().equalsIgnoreCase(source))
                .filter(s -> language == null || language.isEmpty() || language.equalsIgnoreCase("Wszystkie") || s.getLanguage().equalsIgnoreCase(language))
                .collect(Collectors.toList());
    }

    private void addMatches(String word, Map<String, Set<Long>> index, Set<Long> result) {
        Set<Long> matches = index.get(word);
        if (matches != null) {
            result.addAll(matches);
        }
    }

    private boolean matchesQuery(Song song, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;

        String titleNorm = normalizeAndTransliterate(song.getTitle());
        String artistNorm = normalizeAndTransliterate(song.getArtist());

        return titleNorm.contains(normalizedQuery) ||
               artistNorm.contains(normalizedQuery) ||
               (titleNorm + " " + artistNorm).contains(normalizedQuery);
    }

    public List<Song> searchSongs(String query, String source) {
        return searchSongs(query, source, null);
    }

    /**
     * Normalizacja z zachowaniem możliwości dopasowania:
     * - lowercase
     * - transliteracja cyrylicy
     * - transliteracja polskich diakrytyk (ą→a, ć→c, itd.)
     * - usuwa tylko znaki sterujące, zostawia spacje i interpunkcję podstawową
     */
    private String normalizeAndTransliterate(String input) {
        if (input == null) return "";

        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toLowerCase(Locale.ROOT).toCharArray()) {
            // Cyrylica
            String cyr = cyrillicMap.get(c);
            if (cyr != null) {
                sb.append(cyr);
                continue;
            }
            // Polskie diakrytyki
            String pl = polishMap.get(c);
            if (pl != null) {
                sb.append(pl);
                continue;
            }
            // Pozwól: litery, cyfry, spacje, podstawową interpunkcję
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '.' || c == ',' || c == '_') {
                sb.append(c);
            }
            // Inne znaki pomijamy (emoji, symbole, itp.)
        }
        return sb.toString().trim();
    }

    // Mapy transliteracji - static final dla wydajności
    private static final Map<Character, String> cyrillicMap = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "yo"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ы', "y"),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya")
    );

    private static final Map<Character, String> polishMap = Map.ofEntries(
            Map.entry('ą', "a"), Map.entry('ć', "c"), Map.entry('ę', "e"), Map.entry('ł', "l"),
            Map.entry('ń', "n"), Map.entry('ó', "o"), Map.entry('ś', "s"), Map.entry('ź', "z"),
            Map.entry('ż', "z")
    );

    @Deprecated
    private String normalizeText(String input) {
        return normalizeAndTransliterate(input);
    }

    @Deprecated
    private String cyrillicToLatin(String text) {
        return normalizeAndTransliterate(text);
    }

    public QueueItem addToQueue(Long songId, String userName) {
        // Walidacja inputów
        if (songId == null || userName == null || userName.trim().isEmpty()) {
            return null;
        }
        if (userName.length() > 50) {
            userName = userName.substring(0, 50);
        }
        // Sanitizacja - usuń potencjalnie niebezpieczne znaki
        userName = userName.replaceAll("[<>\"'&]", "");

        Song song = allSongsMap.get(songId);
        if (song == null) return null;

        // Ograniczenie: maksymalnie 2 piosenki pod rządem na jedno imię
        // Sprawdź 2 ostatnie aktywne/oczekujące piosenki (nie FINISHED) z tym samym imieniem
        String normalizedName = normalizeNameForComparison(userName);
        long sameNameCount = queue.stream()
                .filter(item -> !"FINISHED".equals(item.getStatus()))
                .filter(item -> normalizeNameForComparison(item.getUserName()).equals(normalizedName))
                .count();

        if (sameNameCount >= 2) {
            // Rzucamy wyjatek – kontroler zamieni na 400 z komunikatem
            throw new IllegalArgumentException(
                    "Możesz Ordering maksymalnie 2 piosenki pod rządem na jedno imię. " +
                    "Poczekaj, aż kto inny doda swój utwór!"
            );
        }

        QueueItem item = new QueueItem(queueIdGenerator.getAndIncrement(), userName.trim(), song);
        queue.add(item);
        return item;
    }

    /**
     * Normalizacja imienia do porównania: lowercase, usunięcie nadmiarowych spacji.
     * Zapobiega podwójnemu zamawianiu pod "Jan" / "  Jan  " / "jan".
     */
    private String normalizeNameForComparison(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public List<QueueItem> getQueue() {
        return queue.stream()
                .filter(item -> !"FINISHED".equals(item.getStatus()))
                .collect(Collectors.toList());
    }

    public void updateStatus(Long queueId, String status) {
        if (queueId == null || status == null) return;
        // Walidacja statusu - whitelist
        if (!Set.of("WAITING", "PLAYING", "FINISHED").contains(status.toUpperCase())) {
            return;
        }
        queue.stream()
                .filter(item -> item.getId().equals(queueId))
                .findFirst()
                .ifPresent(item -> item.setStatus(status.toUpperCase()));
    }

    public void removeFromQueue(Long queueId) {
        if (queueId == null) return;
        queue.removeIf(item -> item.getId().equals(queueId));
    }

    // Metody do administracji / monitoringu
    public int getTotalSongsCount() {
        return allSongsMap.size();
    }

    public int getQueueSize() {
        return (int) queue.stream().filter(i -> !"FINISHED".equals(i.getStatus())).count();
    }
}
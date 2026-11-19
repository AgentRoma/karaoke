package com.example.karaoke.service;

import com.example.karaoke.model.Song;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SongDatabaseInitializer {

    public List<Song> generateInitialCatalog(AtomicLong idGen) {
        List<Song> catalog = new ArrayList<>();

        loadCSV(catalog, idGen, "/karafun_catalog.csv", "Karafan", true);
        loadCSV(catalog, idGen, "/ising_catalog.csv", "iSing", false);

        System.out.println(">>> ŁĄCZNIE W BAZIE KLUBU: " + catalog.size() + " PIOSENEK <<<");
        return catalog;
    }

    private void loadCSV(List<Song> catalog, AtomicLong idGen, String resourcePath, String defaultSource, boolean isKarafunFormat) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("Brak pliku w resources: " + resourcePath);
                return;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;

                while ((line = br.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    String[] cols = line.split(";");
                    if (cols.length >= 2) {
                        String title;
                        String artist;
                        String rawLang;

                        if (isKarafunFormat && cols.length >= 9) {
                            title = cleanString(cols[1]);
                            artist = cleanString(cols[2]);
                            rawLang = cleanString(cols[8]);
                        } else {
                            artist = cleanString(cols[0]);
                            title = cleanString(cols[1]);
                            rawLang = cols.length >= 4 ? cleanString(cols[3]) : "PL";
                        }

                        if (title.isEmpty() || artist.isEmpty()) continue;

                        String langCode = mapLanguageToCode(rawLang);

                        String query = artist + " " + title;
                        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

                        String songUrl = defaultSource.equalsIgnoreCase("iSing")
                                ? "https://www.google.com/search?q=ising+" + encodedQuery
                                : "https://www.google.com/search?q=karafun+" + encodedQuery;

                        catalog.add(new Song(
                                idGen.getAndIncrement(),
                                title,
                                artist,
                                defaultSource,
                                songUrl,
                                langCode
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas czytania " + resourcePath + ": " + e.getMessage());
        }
    }

    private String cleanString(String input) {
        if (input == null) return "";
        return input.replace("\"", "")
                .replace("\u0000", "")
                .trim();
    }

    private String mapLanguageToCode(String rawLang) {
        if (rawLang == null || rawLang.isBlank()) return "EN";
        String upper = rawLang.trim().toUpperCase();

        if (upper.equals("PL") || upper.contains("POLISH") || upper.contains("POLSKI")) return "PL";
        if (upper.equals("EN") || upper.contains("ENGLISH") || upper.contains("ANGIELSKI")) return "EN";
        if (upper.equals("RU") || upper.contains("RUSSIAN") || upper.contains("ROSYJSKI")) return "RU";
        if (upper.equals("UA") || upper.contains("UKRAINIAN") || upper.contains("UKRAIŃSKI")) return "UA";
        if (upper.equals("FR") || upper.contains("FRENCH") || upper.contains("FRANCUSKI")) return "FR";
        if (upper.equals("ES") || upper.contains("SPANISH") || upper.contains("HISZPAŃSKI")) return "ES";
        if (upper.equals("IT") || upper.contains("ITALIAN") || upper.contains("WŁOSKI")) return "IT";
        if (upper.equals("DE") || upper.contains("GERMAN") || upper.contains("NIEMIECKI")) return "DE";

        return upper.length() == 2 ? upper : "EN";
    }
}
package com.example.karaoke.service;

import com.example.karaoke.model.Song;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SongDatabaseInitializer {

    public List<Song> generateInitialCatalog(AtomicLong idGen) {
        List<Song> catalog = new ArrayList<>();

        // Wczytanie plików CSV z katalogami
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
                            // iSing format: Wykonawca (cols[0]); Tytul (cols[1]); Zrodlo (cols[2]); Jezyk (cols[3])
                            artist = cleanString(cols[0]);
                            title = cleanString(cols[1]);
                            rawLang = cols.length >= 4 ? cleanString(cols[3]) : "PL";
                        }

                        if (title.isEmpty() || artist.isEmpty()) continue;

                        String langCode = mapLanguageToCode(rawLang);

                        // Generowanie linku dopasowanego do źródła
                        String songUrl;
                        if (defaultSource.equalsIgnoreCase("iSing")) {
                            songUrl = "https://www.google.com/search?q=ising+" + (artist + "+" + title).replace(" ", "+");
                        } else {
                            songUrl = "https://www.google.com/search?q=karafun+" + (artist + "+" + title).replace(" ", "+");
                        }

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
        return input.replace("\"", "").trim();
    }

    private String mapLanguageToCode(String rawLang) {
        if (rawLang == null) return "EN";
        String upper = rawLang.trim().toUpperCase();

        if (upper.equals("PL") || upper.contains("POLISH")) return "PL";
        if (upper.equals("EN") || upper.contains("ENGLISH")) return "EN";
        if (upper.equals("RU") || upper.contains("RUSSIAN")) return "RU";
        if (upper.equals("UA") || upper.contains("UKRAINIAN")) return "UA";
        if (upper.equals("FR") || upper.contains("FRENCH")) return "FR";
        if (upper.equals("ES") || upper.contains("SPANISH")) return "ES";
        if (upper.equals("IT") || upper.contains("ITALIAN")) return "IT";
        if (upper.equals("DE") || upper.contains("GERMAN")) return "DE";

        return upper.length() == 2 ? upper : "EN";
    }
}
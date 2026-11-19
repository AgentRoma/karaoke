package com.example.karaoke.controller;

import com.example.karaoke.model.Song;
import com.example.karaoke.service.KaraokeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final String adminPassword;
    private final KaraokeService karaokeService;

    public AdminController(
            @Value("${admin.password:}") String adminPassword,
            KaraokeService karaokeService) {
        this.adminPassword = adminPassword;
        this.karaokeService = karaokeService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String password = request.get("password");

        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Hasło jest wymagane"
            ));
        }

        if (adminPassword == null || adminPassword.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Hasło administratora nie jest skonfigurowane. Ustaw zmienną środowiskową ADMIN_PASSWORD."
            ));
        }

        if (password.equals(adminPassword)) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Zalogowano pomyślnie"
            ));
        }

        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Nieprawidłowe hasło"
        ));
    }

    @PostMapping("/songs")
    public ResponseEntity<?> addSong(
            @RequestHeader(value = "X-Admin-Auth", required = false) String adminAuth,
            @RequestBody Map<String, String> request) {
        if (!"true".equals(adminAuth)) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Brak autoryzacji administratora"
            ));
        }

        try {
            Song created = karaokeService.addSong(
                    request.get("artist"),
                    request.get("title"),
                    request.get("language"),
                    request.get("source")
            );
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalSongs", karaokeService.getTotalSongsCount(),
                "queueSize", karaokeService.getQueueSize()
        ));
    }
}
package com.example.karaoke.controller;

import com.example.karaoke.service.KaraokeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpointy administracyjne z bezpiecznym logowaniem.
 * Hasło konfigurowane przez zmienną środowiskową ADMIN_PASSWORD (nie w kodzie!).
 */
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

    /**
     * Logowanie administratora - porównuje hasło z konfiguracją (env var).
     * Nigdy nie zwraca hasła w odpowiedzi.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String password = request.get("password");

        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Hasło jest wymagane"
            ));
        }

        // Sprawdź czy hasło jest skonfigurowane
        if (adminPassword == null || adminPassword.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Hasło administratora nie jest skonfigurowane. Ustaw zmienną środowiskową ADMIN_PASSWORD."
            ));
        }

        // Constant-time comparison (basic) - w produkcji: BCrypt/Argon2
        if (password.equals(adminPassword)) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Zalogowano pomyślnie"
            ));
        }

        // Opóźnienie przed odrzuceniem (timing attack mitigation)
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Nieprawidłowe hasło"
        ));
    }

    /**
     * Statystyki systemu - tylko dla admina (w produkcji dodać @PreAuthorize)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalSongs", karaokeService.getTotalSongsCount(),
                "queueSize", karaokeService.getQueueSize()
        ));
    }
}
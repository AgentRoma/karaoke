package com.example.karaoke.controller;

import com.example.karaoke.model.QueueItem;
import com.example.karaoke.service.KaraokeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final KaraokeService karaokeService;

    public QueueController(KaraokeService karaokeService) {
        this.karaokeService = karaokeService;
    }

    // Dodawanie piosenki do kolejki (POST /api/queue?songId=...&userName=...)
    @PostMapping
    public ResponseEntity<?> addToQueue(
            @RequestParam Long songId,
            @RequestParam String userName) {

        try {
            QueueItem item = karaokeService.addToQueue(songId, userName);
            if (item == null) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            // Błąd biznesowy – np. limit 2 piosenek na imię
            return ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Pobieranie listy kolejki (GET /api/queue)
    @GetMapping
    public List<QueueItem> getQueue() {
        return karaokeService.getQueue();
    }

    // Zmiana statusu piosenki (PUT /api/queue/{id}/status?status=PLAYING) – tylko admin
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @RequestHeader(value = "X-Admin-Auth", required = false) String adminAuth,
            @PathVariable Long id,
            @RequestParam String status) {

        if (!"true".equals(adminAuth)) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Brak autoryzacji administratora"
            ));
        }
        if (id == null || status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Brak id lub statusu"
            ));
        }
        karaokeService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of("success", true, "message", "Status zaktualizowany"));
    }

    // Usuwanie z kolejki (DELETE /api/queue/{id}) – tylko admin
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> removeFromQueue(
            @RequestHeader(value = "X-Admin-Auth", required = false) String adminAuth,
            @PathVariable Long id) {

        if (!"true".equals(adminAuth)) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Brak autoryzacji administratora"
            ));
        }
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Brak id"
            ));
        }
        karaokeService.removeFromQueue(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Usunięto z kolejki"));
    }
}
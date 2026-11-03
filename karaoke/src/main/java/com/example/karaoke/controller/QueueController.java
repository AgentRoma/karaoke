package com.example.karaoke.controller;

import com.example.karaoke.model.QueueItem;
import com.example.karaoke.service.KaraokeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final KaraokeService karaokeService;

    public QueueController(KaraokeService karaokeService) {
        this.karaokeService = karaokeService;
    }

    // Dodawanie piosenki do kolejki (POST /api/queue?songId=...&userName=...)
    @PostMapping
    public ResponseEntity<QueueItem> addToQueue(
            @RequestParam Long songId,
            @RequestParam String userName) {

        QueueItem item = karaokeService.addToQueue(songId, userName);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    // Pobieranie listy kolejki (GET /api/queue)
    @GetMapping
    public List<QueueItem> getQueue() {
        return karaokeService.getQueue();
    }

    // Zmiana statusu piosenki (PUT /api/queue/{id}/status?status=PLAYING)
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        karaokeService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    // Usuwanie z kolejki (DELETE /api/queue/{id})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFromQueue(@PathVariable Long id) {
        karaokeService.removeFromQueue(id);
        return ResponseEntity.ok().build();
    }
}
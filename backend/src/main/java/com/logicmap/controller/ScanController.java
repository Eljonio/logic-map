package com.logicmap.controller;

import com.logicmap.model.ScanRequest;
import com.logicmap.model.ScanResult;
import com.logicmap.service.MapService;
import com.logicmap.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScanController {

    private final ScanService scanService;
    private final MapService mapService;

    public ScanController(ScanService scanService, MapService mapService) {
        this.scanService = scanService;
        this.mapService = mapService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody ScanRequest request) {
        try {
            ScanResult result = scanService.scan(request);
            mapService.importMap(result);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to scan project: " + e.getMessage()));
        }
    }
}

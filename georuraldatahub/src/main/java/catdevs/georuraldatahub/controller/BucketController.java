package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import catdevs.georuraldatahub.service.BucketService;

@RestController
@RequestMapping("/bucket")
public class BucketController {

    private final BucketService bucketService;

    public BucketController(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping
    public List<String> listObjects() {
        return bucketService.listObjectsBucket();
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> readGeoJson(@RequestParam String objectName) throws Exception {
        JsonNode geoJson = bucketService.readGeoJson(objectName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(geoJson.toString());
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping(value = "/csv-geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> readCsvAsGeoJson(
            @RequestParam String objectName) throws Exception {

        JsonNode geoJson = bucketService.readCsvAsGeoJson(objectName);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(geoJson.toString());
    }
}
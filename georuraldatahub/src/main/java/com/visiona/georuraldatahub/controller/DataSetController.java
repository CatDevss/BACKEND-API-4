package com.visiona.georuraldatahub.controller;

import com.visiona.georuraldatahub.dto.DataSetResponseDTO;
import com.visiona.georuraldatahub.service.DataSetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/datasets")
public class DataSetController {

    private final DataSetService dataSetService;

    public DataSetController(DataSetService dataSetService) {
        this.dataSetService = dataSetService;
    }

    @GetMapping
    public ResponseEntity<List<DataSetResponseDTO>> listDataSets(
            @RequestParam(required = false) Long sourceId) {
        return ResponseEntity.ok(dataSetService.list(sourceId));
    }
}
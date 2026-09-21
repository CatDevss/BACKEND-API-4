package com.visiona.georuraldatahub.service;

import com.visiona.georuraldatahub.dto.DataSetResponseDTO;
import com.visiona.georuraldatahub.model.DataSet;
import com.visiona.georuraldatahub.repository.DataSetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataSetService {

    private final DataSetRepository dataSetRepository;

    public DataSetService(DataSetRepository dataSetRepository) {
        this.dataSetRepository = dataSetRepository;
    }

    public List<DataSetResponseDTO> list(Long sourceId) {
        List<DataSet> dataSets = (sourceId != null)
                ? dataSetRepository.findBySource_Id(sourceId)
                : dataSetRepository.findAll();

        return dataSets.stream()
                .map(this::toDto)
                .toList();
    }

    private DataSetResponseDTO toDto(DataSet dataSet) {
        return new DataSetResponseDTO(
                dataSet.getId(),
                dataSet.getName(),
                dataSet.getSource() != null ? dataSet.getSource().getId() : null,
                dataSet.getSource() != null ? dataSet.getSource().getName() : null
        );
    }
}
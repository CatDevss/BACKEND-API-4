package com.visiona.georuraldatahub.dto;

public class DataSetResponseDTO {

    private Long id;
    private String name;
    private Long sourceId;
    private String sourceName;

    public DataSetResponseDTO() {
    }

    public DataSetResponseDTO(Long id, String name, Long sourceId, String sourceName) {
        this.id = id;
        this.name = name;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
}
package com.visiona.georuraldatahub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fonte")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fon_id")
    private Long id;

    @Column(name = "fon_nome", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "fon_data_criacao", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "fon_url", nullable = false, length = 500)
    private String url;

    @Column(name = "usr_id", nullable = false)
    private Long userId;

    public Source() {
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

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
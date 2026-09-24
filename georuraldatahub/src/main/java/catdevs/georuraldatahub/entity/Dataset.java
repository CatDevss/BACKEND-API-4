package catdevs.georuraldatahub.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "conjunto")
public class Dataset {

    @Id
    @Column(name = "con_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "con_nome", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fon_id", nullable = false)
    private Source source;

    public Dataset() {
    }

    public Dataset(String name, Source source) {
        this.name = name;
        this.source = source;
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

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }
}
package catdevs.georuraldatahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "arquivo")
public class File {

    @Id
    @Column(name = "arq_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", referencedColumnName = "ver_id")
    private Version version;

    @Column(name = "arq_nome")
    private String name;

    @Column(name = "arq_formato")
    private String formatFile;

    @Column(name = "arq_hash")
    private String hash;

    @Column(name = "arq_localizacao")
    private String location;

    public File(){
    }

    public File(Version version, String name, String formatFile, String hash, String location) {
        this.version = version;
        this.name = name;
        this.formatFile = formatFile;
        this.hash = hash;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormatFile() {
        return formatFile;
    }

    public void setFormatFile(String formatFile) {
        this.formatFile = formatFile;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
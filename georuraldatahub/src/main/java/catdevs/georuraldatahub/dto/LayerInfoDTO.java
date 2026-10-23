package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public class LayerInfoDTO {

    private Long id;
    private String name;
    private String formatFile;
    private Long size;
    private LocalDateTime receivedDate;

    public LayerInfoDTO(
            Long id,
            String name,
            String formatFile,
            Long size,
            LocalDateTime receivedDate
    ) {
        this.id = id;
        this.name = name;
        this.formatFile = formatFile;
        this.size = size;
        this.receivedDate = receivedDate;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFormatFile() {
        return formatFile;
    }

    public Long getSize() {
        return size;
    }

    public LocalDateTime getReceivedDate() {
        return receivedDate;
    }
}
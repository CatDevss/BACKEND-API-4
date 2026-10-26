package catdevs.georuraldatahub.validation;

import java.util.List;

public record FileValidationReport(String status, Long dictionaryId, Integer dictionaryVersion,
        long validRecords, long rejectedRecords, List<RecordValidationResult> records) {
    public static FileValidationReport of(Long id, Integer version, List<RecordValidationResult> records) {
        long valid = records.stream().filter(RecordValidationResult::valid).count();
        long invalid = records.stream().filter(r -> !r.valid() && r.recordIndex() >= 0).count();
        boolean fileError = records.stream().anyMatch(r -> r.recordIndex() < 0);
        return new FileValidationReport(fileError || valid == 0 ? "REJEITADO" : invalid > 0 ? "PARCIAL" : "VALIDO",
                id, version, valid, invalid, List.copyOf(records));
    }
}

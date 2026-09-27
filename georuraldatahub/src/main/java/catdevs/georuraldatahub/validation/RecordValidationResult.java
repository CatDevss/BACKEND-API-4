package catdevs.georuraldatahub.validation;

import java.util.List;

public record RecordValidationResult(
        int recordIndex,
        String rawContent,
        boolean valid,
        List<String> errors
) {
    public static RecordValidationResult ok(int index, String raw) {
        return new RecordValidationResult(index, raw, true, List.of());
    }

    public static RecordValidationResult invalid(int index, String raw, List<String> errors) {
        return new RecordValidationResult(index, raw, false, errors);
    }
}
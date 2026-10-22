package catdevs.georuraldatahub.validation;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FieldRule(
        @NotBlank String name,
        boolean required,
        @NotNull FieldType type,
        List<String> domain,
        boolean unique
) {
    public FieldRule {
        domain = domain == null ? List.of() : List.copyOf(domain);
    }
    public FieldRule(String name, boolean required, FieldType type) {
        this(name, required, type, List.of(), false);
    }
    public static FieldRule required(String name, FieldType type) {
        return new FieldRule(name, true, type);
    }
    public static FieldRule optional(String name, FieldType type) {
        return new FieldRule(name, false, type);
    }
}

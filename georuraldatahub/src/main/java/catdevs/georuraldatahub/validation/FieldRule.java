package catdevs.georuraldatahub.validation;

public record FieldRule(
        String name,
        boolean required,
        FieldType type
) {
    public static FieldRule required(String name, FieldType type) {
        return new FieldRule(name, true, type);
    }

    public static FieldRule optional(String name, FieldType type) {
        return new FieldRule(name, false, type);
    }
}
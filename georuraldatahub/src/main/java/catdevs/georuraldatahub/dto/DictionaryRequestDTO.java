package catdevs.georuraldatahub.dto;

import java.util.List;
import catdevs.georuraldatahub.validation.FieldRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record DictionaryRequestDTO(@NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description, @NotEmpty List<@NotNull @Valid FieldRule> rules) {}

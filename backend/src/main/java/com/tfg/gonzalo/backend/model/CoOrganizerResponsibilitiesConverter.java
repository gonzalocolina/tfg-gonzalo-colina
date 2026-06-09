package com.tfg.gonzalo.backend.model;

import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class CoOrganizerResponsibilitiesConverter implements AttributeConverter<Set<CoOrganizerResponsibility>, String> {

    @Override
    public String convertToDatabaseColumn(Set<CoOrganizerResponsibility> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }

        return attribute.stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<CoOrganizerResponsibility> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashSet<>();
        }

        return Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(CoOrganizerResponsibility::valueOf)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
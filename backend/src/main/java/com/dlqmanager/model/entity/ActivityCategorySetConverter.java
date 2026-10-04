package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.ActivityCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stores a set of activity categories in one text column, e.g. "REPLAYS,ALERTS"
 *
 * A plain column keeps the channel in a single table and needs no change to existing rows:
 * an empty column means the channel follows nothing.
 */
@Converter
public class ActivityCategorySetConverter implements AttributeConverter<Set<ActivityCategory>, String> {

    @Override
    public String convertToDatabaseColumn(Set<ActivityCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        // EnumSet order = declaration order, so the stored text is always the same for the same set
        return EnumSet.copyOf(categories).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<ActivityCategory> convertToEntityAttribute(String column) {
        Set<ActivityCategory> categories = EnumSet.noneOf(ActivityCategory.class);
        if (column == null || column.isBlank()) {
            return categories;
        }
        for (String name : column.split(",")) {
            try {
                categories.add(ActivityCategory.valueOf(name.trim()));
            } catch (IllegalArgumentException e) {
                // A category this version doesn't know (written by a newer one): skip it
            }
        }
        return categories;
    }
}

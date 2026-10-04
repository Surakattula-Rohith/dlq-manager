package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.ActivityCategory;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityCategorySetConverterTest {

    private final ActivityCategorySetConverter converter = new ActivityCategorySetConverter();

    @Test
    void storesTheCategoriesAsOneLineOfText() {
        // Always in the same order, whatever order they were chosen in
        assertThat(converter.convertToDatabaseColumn(Set.of(ActivityCategory.CHANGES, ActivityCategory.REPLAYS)))
                .isEqualTo("REPLAYS,CHANGES");
        assertThat(converter.convertToEntityAttribute("REPLAYS,CHANGES"))
                .containsExactly(ActivityCategory.REPLAYS, ActivityCategory.CHANGES);
    }

    @Test
    void channelsSavedBeforeTheTeamFeedExistedFollowNothing() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
        assertThat(converter.convertToDatabaseColumn(EnumSet.noneOf(ActivityCategory.class))).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void aCategoryThisVersionDoesNotKnowIsIgnored() {
        assertThat(converter.convertToEntityAttribute("REPLAYS, SOMETHING_NEWER ,ALERTS"))
                .containsExactly(ActivityCategory.REPLAYS, ActivityCategory.ALERTS);
    }

    @Test
    void theSetReadFromTheDatabaseCanBeChanged() {
        Set<ActivityCategory> categories = converter.convertToEntityAttribute(null);
        categories.add(ActivityCategory.ALERTS);

        assertThat(categories).containsExactly(ActivityCategory.ALERTS);
    }
}

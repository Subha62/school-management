package com.schoolmanagement.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schoolmanagement.common.exception.InvalidRequestException;
import com.schoolmanagement.config.PaginationProperties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestFactoryTest {

    private static final Set<String> SORTABLE = Set.of("lastName", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by("createdAt").descending();

    private final PageRequestFactory factory = new PageRequestFactory(new PaginationProperties(20, 100));

    @Test
    void appliesDefaultsWhenNothingIsRequested() {
        Pageable pageable = factory.create(null, null, null, null, SORTABLE, DEFAULT_SORT);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    void honoursWhitelistedSortAndDirection() {
        Pageable pageable = factory.create(2, 50, "lastName", "desc", SORTABLE, DEFAULT_SORT);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "lastName"));
    }

    @Test
    void rejectsPageSizeAboveMaximum() {
        assertThatThrownBy(() -> factory.create(0, 101, null, null, SORTABLE, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("between 1 and 100");
    }

    @Test
    void rejectsNonPositivePageSizeAndNegativePage() {
        assertThatThrownBy(() -> factory.create(0, 0, null, null, SORTABLE, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> factory.create(-1, 10, null, null, SORTABLE, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsSortFieldsOutsideTheWhitelist() {
        assertThatThrownBy(() -> factory.create(0, 10, "passwordHash", "asc", SORTABLE, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsUnknownSortDirection() {
        assertThatThrownBy(() -> factory.create(0, 10, "lastName", "sideways", SORTABLE, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class);
    }
}

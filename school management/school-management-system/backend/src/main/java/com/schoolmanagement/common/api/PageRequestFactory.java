package com.schoolmanagement.common.api;

import com.schoolmanagement.common.exception.InvalidRequestException;
import com.schoolmanagement.config.PaginationProperties;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Builds bounded {@link Pageable} instances from request parameters. Sorting is limited to a whitelist. */
@Component
public class PageRequestFactory {

    private final PaginationProperties properties;

    public PageRequestFactory(PaginationProperties properties) {
        this.properties = properties;
    }

    public Pageable create(Integer page, Integer size, String sortBy, String direction, Set<String> sortableFields,
            Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? properties.defaultPageSize() : size;
        if (pageNumber < 0) {
            throw new InvalidRequestException("Page index must not be negative.");
        }
        if (pageSize < 1 || pageSize > properties.maxPageSize()) {
            throw new InvalidRequestException("Page size must be between 1 and " + properties.maxPageSize() + ".");
        }
        return PageRequest.of(pageNumber, pageSize, resolveSort(sortBy, direction, sortableFields, defaultSort));
    }

    private static Sort resolveSort(String sortBy, String direction, Set<String> sortableFields, Sort defaultSort) {
        if (sortBy == null || sortBy.isBlank()) {
            return defaultSort;
        }
        if (!sortableFields.contains(sortBy)) {
            throw new InvalidRequestException("Sorting by '" + sortBy + "' is not supported.");
        }
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (direction != null && !direction.isBlank() && !"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new InvalidRequestException("Sort direction must be 'asc' or 'desc'.");
        }
        return Sort.by(sortDirection, sortBy);
    }
}

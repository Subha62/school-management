package com.schoolmanagement.common.persistence;

import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.InvalidRequestException;

/** Optimistic-lock check for updates: the client sends the version it last saw. */
public final class OptimisticVersion {

    private OptimisticVersion() {
    }

    public static void check(Long requested, long current) {
        if (requested == null) {
            throw new InvalidRequestException("The current record version is required.");
        }
        if (requested != current) {
            throw new ConflictException("This record was changed by someone else. Reload it and try again.");
        }
    }
}

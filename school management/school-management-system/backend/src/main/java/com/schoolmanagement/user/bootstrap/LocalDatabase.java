package com.schoolmanagement.user.bootstrap;

/** Shared guard: demo data is only ever written to a database on the local machine. */
final class LocalDatabase {

    private LocalDatabase() {
    }

    static boolean isLocal(String jdbcUrl) {
        return jdbcUrl.contains("//localhost") || jdbcUrl.contains("//127.0.0.1") || jdbcUrl.contains("//[::1]");
    }
}

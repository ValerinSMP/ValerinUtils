package me.mtynnn.valerinutils.commands;

import java.util.List;

final class HelpPaginator {
    private HelpPaginator() {
    }

    static <T> Page<T> page(List<T> entries, int requestedPage, int pageSize) {
        int safeSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (entries.size() + safeSize - 1) / safeSize);
        if (requestedPage < 1 || requestedPage > totalPages) {
            return new Page<>(requestedPage, totalPages, List.of(), false);
        }
        int from = (requestedPage - 1) * safeSize;
        int to = Math.min(entries.size(), from + safeSize);
        return new Page<>(requestedPage, totalPages, entries.subList(from, to), true);
    }

    record Page<T>(int number, int totalPages, List<T> entries, boolean valid) {
    }
}

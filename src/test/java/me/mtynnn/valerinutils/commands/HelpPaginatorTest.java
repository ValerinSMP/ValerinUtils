package me.mtynnn.valerinutils.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpPaginatorTest {
    @Test
    void slicesEntriesWithoutDroppingTheLastPage() {
        var page = HelpPaginator.page(List.of(1, 2, 3, 4, 5), 2, 3);

        assertTrue(page.valid());
        assertEquals(2, page.totalPages());
        assertEquals(List.of(4, 5), page.entries());
    }

    @Test
    void rejectsPagesOutsideTheAvailableRange() {
        assertFalse(HelpPaginator.page(List.of(1), 0, 9).valid());
        assertFalse(HelpPaginator.page(List.of(1), 2, 9).valid());
    }
}

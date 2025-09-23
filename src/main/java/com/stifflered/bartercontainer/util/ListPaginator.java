package com.stifflered.bartercontainer.util;

import java.util.Collections;
import java.util.List;

/**
 * Utility for splitting a list of items into fixed-size "pages".
 *
 * @param <T> the type of element contained in the paginated list
 */
public class ListPaginator<T> {

    /** Backing list of all items to paginate. */
    private final List<T> items;

    /** Maximum number of items per page (must be > 0). */
    private final int pageSize;

    /**
     * Construct a new paginator for the given list.
     *
     * @param items    full list of items (not copied, referenced directly)
     * @param pageSize number of items per page (must be > 0)
     */
    public ListPaginator(List<T> items, int pageSize) {
        if (pageSize <= 0) {
            // Was: "Page size must be greater than zero."
            // Now pulled from messages.yml so server owners can theme/translate.
            throw new IllegalArgumentException(Messages.fmt("util.paginator.page_size_invalid"));
        }
        this.items = items;
        this.pageSize = pageSize;
    }

    /**
     * Get the items for a given page number.
     *
     * @param pageNumber zero-based page index (0 = first page)
     * @return immutable list of items on that page
     *         (empty if pageNumber is beyond the last page)
     * @throws IllegalArgumentException if pageNumber is negative
     */
    public List<T> getPage(int pageNumber) {
        if (pageNumber < 0) {
            // Was: "Page number must be non-negative."
            throw new IllegalArgumentException(Messages.fmt("util.paginator.page_number_negative"));
        }
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, items.size());

        if (fromIndex >= items.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(items.subList(fromIndex, toIndex));
    }

    /**
     * Total number of pages required to display all items.
     *
     * @return page count (≥ 0, with 0 meaning no items at all)
     */
    public int getTotalPages() {
        return (int) Math.ceil((double) items.size() / pageSize);
    }
}

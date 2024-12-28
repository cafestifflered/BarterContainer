package com.stifflered.bartercontainer.util;

import java.util.Collections;
import java.util.List;

public class ListPaginator<T> {

    private final List<T> items;
    private final int pageSize;

    public ListPaginator(List<T> items, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be greater than zero.");
        }
        this.items = items;
        this.pageSize = pageSize;
    }

    public List<T> getPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number must be non-negative.");
        }
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        if (fromIndex >= items.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(items.subList(fromIndex, toIndex));
    }

    public int getTotalPages() {
        return (int) Math.ceil((double) items.size() / pageSize);
    }
}
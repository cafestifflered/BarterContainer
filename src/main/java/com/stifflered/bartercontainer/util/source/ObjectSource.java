package com.stifflered.bartercontainer.util.source;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ObjectSource<K, T> {

    boolean delete(@NotNull T type) throws Exception;

    boolean save(@NotNull T type) throws Exception;

    @Nullable
    T load(@NotNull K key) throws Exception;

}

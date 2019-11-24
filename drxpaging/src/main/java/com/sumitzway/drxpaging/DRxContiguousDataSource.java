package com.sumitzway.drxpaging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

abstract class DRxContiguousDataSource<Key, Value> extends DRxDataSource<Key, Value> {
    @Override
    boolean isContiguous() {
        return true;
    }

    abstract void dispatchLoadInitial(
            @Nullable Key key,
            int initialLoadSize,
            int pageSize,
            boolean enablePlaceholders,
            @NonNull Executor mainThreadExecutor,
            @NonNull DRxPageResult.Receiver<Value> receiver);

    abstract void dispatchLoadAfter(
            int currentEndIndex,
            @NonNull Value currentEndItem,
            int pageSize,
            @NonNull Executor mainThreadExecutor,
            @NonNull DRxPageResult.Receiver<Value> receiver);

    abstract void dispatchLoadBefore(
            int currentBeginIndex,
            @NonNull Value currentBeginItem,
            int pageSize,
            @NonNull Executor mainThreadExecutor,
            @NonNull DRxPageResult.Receiver<Value> receiver);

    /**
     * Get the key from either the position, or item, or null if position/item invalid.
     * <p>
     * Position may not match passed item's position - if trying to query the key from a position
     * that isn't yet loaded, a fallback item (last loaded item accessed) will be passed.
     */
    abstract Key getKey(int position, Value item);

    boolean supportsPageDropping() {
        return true;
    }
}

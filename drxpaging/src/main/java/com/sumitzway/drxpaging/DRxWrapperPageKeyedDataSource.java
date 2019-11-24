package com.sumitzway.drxpaging;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;

import java.util.List;

class DRxWrapperPageKeyedDataSource<K, A, B> extends DRxPageKeyedDataSource<K, B> {
    private final DRxPageKeyedDataSource<K, A> mSource;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Function<List<A>, List<B>> mListFunction;

    DRxWrapperPageKeyedDataSource(DRxPageKeyedDataSource<K, A> source,
                                  Function<List<A>, List<B>> listFunction) {
        mSource = source;
        mListFunction = listFunction;
    }

    @Override
    public void addInvalidatedCallback(@NonNull InvalidatedCallback onInvalidatedCallback) {
        mSource.addInvalidatedCallback(onInvalidatedCallback);
    }

    @Override
    public void removeInvalidatedCallback(@NonNull InvalidatedCallback onInvalidatedCallback) {
        mSource.removeInvalidatedCallback(onInvalidatedCallback);
    }

    @Override
    public void invalidate() {
        mSource.invalidate();
    }

    @Override
    public boolean isInvalid() {
        return mSource.isInvalid();
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<K> params,
                            final @NonNull LoadInitialCallback<K, B> callback) {
        mSource.loadInitial(params, new LoadInitialCallback<K, A>() {
            @Override
            public void onResult(@NonNull List<A> data, int position, int totalCount,
                                 @Nullable K previousPageKey, @Nullable K nextPageKey) {
                callback.onResult(convert(mListFunction, data), position, totalCount,
                        previousPageKey, nextPageKey);
            }

            @Override
            public void onResult(@NonNull List<A> data, @Nullable K previousPageKey,
                                 @Nullable K nextPageKey) {
                callback.onResult(convert(mListFunction, data), previousPageKey, nextPageKey);
            }
        });
    }

    @Override
    public void loadBefore(@NonNull LoadParams<K> params,
                           final @NonNull LoadCallback<K, B> callback) {
        mSource.loadBefore(params, new LoadCallback<K, A>() {
            @Override
            public void onResult(@NonNull List<A> data, @Nullable K adjacentPageKey) {
                callback.onResult(convert(mListFunction, data), adjacentPageKey);
            }
        });
    }

    @Override
    public void loadAfter(@NonNull LoadParams<K> params,
                          final @NonNull LoadCallback<K, B> callback) {
        mSource.loadAfter(params, new LoadCallback<K, A>() {
            @Override
            public void onResult(@NonNull List<A> data, @Nullable K adjacentPageKey) {
                callback.onResult(convert(mListFunction, data), adjacentPageKey);
            }
        });
    }
}

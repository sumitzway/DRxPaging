package com.sumitzway.drxpaging;


import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import java.util.List;

class DRxWrapperPositionalDataSource<A, B> extends DRxPositionalDataSource<B> {
    private final DRxPositionalDataSource<A> mSource;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Function<List<A>, List<B>> mListFunction;

    DRxWrapperPositionalDataSource(DRxPositionalDataSource<A> source,
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
    public void loadInitial(@NonNull LoadInitialParams params,
                            final @NonNull LoadInitialCallback<B> callback) {
        mSource.loadInitial(params, new LoadInitialCallback<A>() {
            @Override
            public void onResult(@NonNull List<A> data, int position, int totalCount) {
                callback.onResult(convert(mListFunction, data), position, totalCount);
            }

            @Override
            public void onResult(@NonNull List<A> data, int position) {
                callback.onResult(convert(mListFunction, data), position);
            }
        });
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params,
                          final @NonNull LoadRangeCallback<B> callback) {
        mSource.loadRange(params, new LoadRangeCallback<A>() {
            @Override
            public void onResult(@NonNull List<A> data) {
                callback.onResult(convert(mListFunction, data));
            }
        });
    }
}

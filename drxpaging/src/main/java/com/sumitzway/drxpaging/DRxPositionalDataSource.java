package com.sumitzway.drxpaging;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class DRxPositionalDataSource<T> extends DRxDataSource<Integer, T> {

    /**
     * Holder object for inputs to {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)}.
     */
    @SuppressWarnings("WeakerAccess")
    public static class LoadInitialParams {
        /**
         * Initial load position requested.
         * <p>
         * Note that this may not be within the bounds of your data set, it may need to be adjusted
         * before you execute your load.
         */
        public final int requestedStartPosition;

        /**
         * Requested number of items to load.
         * <p>
         * Note that this may be larger than available data.
         */
        public final int requestedLoadSize;

        /**
         * Defines page size acceptable for return values.
         * <p>
         * List of items passed to the callback must be an integer multiple of page size.
         */
        public final int pageSize;

        /**
         * Defines whether placeholders are enabled, and whether the total count passed to
         * {@link DRxPositionalDataSource.LoadInitialCallback#onResult(List, int, int)} will be ignored.
         */
        public final boolean placeholdersEnabled;

        public LoadInitialParams(
                int requestedStartPosition,
                int requestedLoadSize,
                int pageSize,
                boolean placeholdersEnabled) {
            this.requestedStartPosition = requestedStartPosition;
            this.requestedLoadSize = requestedLoadSize;
            this.pageSize = pageSize;
            this.placeholdersEnabled = placeholdersEnabled;
        }
    }

    /**
     * Holder object for inputs to {@link #loadRange(DRxPositionalDataSource.LoadRangeParams, DRxPositionalDataSource.LoadRangeCallback)}.
     */
    @SuppressWarnings("WeakerAccess")
    public static class LoadRangeParams {
        /**
         * Start position of data to load.
         * <p>
         * Returned data must start at this position.
         */
        public final int startPosition;
        /**
         * Number of items to load.
         * <p>
         * Returned data must be of this size, unless at end of the list.
         */
        public final int loadSize;

        public LoadRangeParams(int startPosition, int loadSize) {
            this.startPosition = startPosition;
            this.loadSize = loadSize;
        }
    }

    /**
     * Callback for {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)}
     * to return data, position, and count.
     * <p>
     * A callback should be called only once, and may throw if called again.
     * <p>
     * It is always valid for a DRxDataSource loading method that takes a callback to stash the
     * callback and call it later. This enables DataSources to be fully asynchronous, and to handle
     * temporary, recoverable error states (such as a network error that can be retried).
     *
     * @param <T> Type of items being loaded.
     */
    public abstract static class LoadInitialCallback<T> {
        /**
         * Called to pass initial load state from a DRxDataSource.
         * <p>
         * Call this method from your DRxDataSource's {@code loadInitial} function to return data,
         * and inform how many placeholders should be shown before and after. If counting is cheap
         * to compute (for example, if a network load returns the information regardless), it's
         * recommended to pass the total size to the totalCount parameter. If placeholders are not
         * requested (when {@link DRxPositionalDataSource.LoadInitialParams#placeholdersEnabled} is false), you can instead
         * call {@link #onResult(List, int)}.
         *
         * @param data List of items loaded from the DRxDataSource. If this is empty, the DRxDataSource
         *             is treated as empty, and no further loads will occur.
         * @param position Position of the item at the front of the list. If there are {@code N}
         *                 items before the items in data that can be loaded from this DRxDataSource,
         *                 pass {@code N}.
         * @param totalCount Total number of items that may be returned from this DRxDataSource.
         *                   Includes the number in the initial {@code data} parameter
         *                   as well as any items that can be loaded in front or behind of
         *                   {@code data}.
         */
        public abstract void onResult(@NonNull List<T> data, int position, int totalCount);

        /**
         * Called to pass initial load state from a DRxDataSource without total count,
         * when placeholders aren't requested.
         * <p class="note"><strong>Note:</strong> This method can only be called when placeholders
         * are disabled ({@link DRxPositionalDataSource.LoadInitialParams#placeholdersEnabled} is false).
         * <p>
         * Call this method from your DRxDataSource's {@code loadInitial} function to return data,
         * if position is known but total size is not. If placeholders are requested, call the three
         * parameter variant: {@link #onResult(List, int, int)}.
         *
         * @param data List of items loaded from the DRxDataSource. If this is empty, the DRxDataSource
         *             is treated as empty, and no further loads will occur.
         * @param position Position of the item at the front of the list. If there are {@code N}
         *                 items before the items in data that can be provided by this DRxDataSource,
         *                 pass {@code N}.
         */
        public abstract void onResult(@NonNull List<T> data, int position);
    }

    /**
     * Callback for DRxPositionalDataSource {@link #loadRange(DRxPositionalDataSource.LoadRangeParams, DRxPositionalDataSource.LoadRangeCallback)}
     * to return data.
     * <p>
     * A callback should be called only once, and may throw if called again.
     * <p>
     * It is always valid for a DRxDataSource loading method that takes a callback to stash the
     * callback and call it later. This enables DataSources to be fully asynchronous, and to handle
     * temporary, recoverable error states (such as a network error that can be retried).
     *
     * @param <T> Type of items being loaded.
     */
    public abstract static class LoadRangeCallback<T> {
        /**
         * Called to pass loaded data from {@link #loadRange(DRxPositionalDataSource.LoadRangeParams, DRxPositionalDataSource.LoadRangeCallback)}.
         *
         * @param data List of items loaded from the DRxDataSource. Must be same size as requested,
         *             unless at end of list.
         */
        public abstract void onResult(@NonNull List<T> data);
    }

    static class LoadInitialCallbackImpl<T> extends DRxPositionalDataSource.LoadInitialCallback<T> {
        final LoadCallbackHelper<T> mCallbackHelper;
        private final boolean mCountingEnabled;
        private final int mPageSize;

        LoadInitialCallbackImpl(@NonNull DRxPositionalDataSource dataSource, boolean countingEnabled,
                                int pageSize, DRxPageResult.Receiver<T> receiver) {
            mCallbackHelper = new LoadCallbackHelper<>(dataSource, DRxPageResult.INIT, null, receiver);
            mCountingEnabled = countingEnabled;
            mPageSize = pageSize;
            if (mPageSize < 1) {
                throw new IllegalArgumentException("Page size must be non-negative");
            }
        }

        @Override
        public void onResult(@NonNull List<T> data, int position, int totalCount) {
            if (!mCallbackHelper.dispatchInvalidResultIfInvalid()) {
                LoadCallbackHelper.validateInitialLoadParams(data, position, totalCount);
                if (position + data.size() != totalCount
                        && data.size() % mPageSize != 0) {
                    throw new IllegalArgumentException("DRxPositionalDataSource requires initial load"
                            + " size to be a multiple of page size to support internal tiling."
                            + " loadSize " + data.size() + ", position " + position
                            + ", totalCount " + totalCount + ", pageSize " + mPageSize);
                }

                if (mCountingEnabled) {
                    int trailingUnloadedCount = totalCount - position - data.size();
                    mCallbackHelper.dispatchResultToReceiver(
                            new DRxPageResult<>(data, position, trailingUnloadedCount, 0));
                } else {
                    // Only occurs when wrapped as contiguous
                    mCallbackHelper.dispatchResultToReceiver(new DRxPageResult<>(data, position));
                }
            }
        }

        @Override
        public void onResult(@NonNull List<T> data, int position) {
            if (!mCallbackHelper.dispatchInvalidResultIfInvalid()) {
                if (position < 0) {
                    throw new IllegalArgumentException("Position must be non-negative");
                }
                if (data.isEmpty() && position != 0) {
                    throw new IllegalArgumentException(
                            "Initial result cannot be empty if items are present in data set.");
                }
                if (mCountingEnabled) {
                    throw new IllegalStateException("Placeholders requested, but totalCount not"
                            + " provided. Please call the three-parameter onResult method, or"
                            + " disable placeholders in the DRxPagedList.Config");
                }
                mCallbackHelper.dispatchResultToReceiver(new DRxPageResult<>(data, position));
            }
        }
    }

    static class LoadRangeCallbackImpl<T> extends DRxPositionalDataSource.LoadRangeCallback<T> {
        private LoadCallbackHelper<T> mCallbackHelper;
        private final int mPositionOffset;
        LoadRangeCallbackImpl(@NonNull DRxPositionalDataSource dataSource,
                              @DRxPageResult.ResultType int resultType, int positionOffset,
                              Executor mainThreadExecutor, DRxPageResult.Receiver<T> receiver) {
            mCallbackHelper = new LoadCallbackHelper<>(
                    dataSource, resultType, mainThreadExecutor, receiver);
            mPositionOffset = positionOffset;
        }

        @Override
        public void onResult(@NonNull List<T> data) {
            if (!mCallbackHelper.dispatchInvalidResultIfInvalid()) {
                mCallbackHelper.dispatchResultToReceiver(new DRxPageResult<>(
                        data, 0, 0, mPositionOffset));
            }
        }
    }

    final void dispatchLoadInitial(boolean acceptCount,
                                   int requestedStartPosition, int requestedLoadSize, int pageSize,
                                   @NonNull Executor mainThreadExecutor, @NonNull DRxPageResult.Receiver<T> receiver) {
        DRxPositionalDataSource.LoadInitialCallbackImpl<T> callback =
                new DRxPositionalDataSource.LoadInitialCallbackImpl<>(this, acceptCount, pageSize, receiver);

        DRxPositionalDataSource.LoadInitialParams params = new DRxPositionalDataSource.LoadInitialParams(
                requestedStartPosition, requestedLoadSize, pageSize, acceptCount);
        loadInitial(params, callback);

        // If initialLoad's callback is not called within the body, we force any following calls
        // to post to the UI thread. This constructor may be run on a background thread, but
        // after constructor, mutation must happen on UI thread.
        callback.mCallbackHelper.setPostExecutor(mainThreadExecutor);
    }

    final void dispatchLoadRange(@DRxPageResult.ResultType int resultType, int startPosition,
                                 int count, @NonNull Executor mainThreadExecutor,
                                 @NonNull DRxPageResult.Receiver<T> receiver) {
        DRxPositionalDataSource.LoadRangeCallback<T> callback = new DRxPositionalDataSource.LoadRangeCallbackImpl<>(
                this, resultType, startPosition, mainThreadExecutor, receiver);
        if (count == 0) {
            callback.onResult(Collections.<T>emptyList());
        } else {
            loadRange(new DRxPositionalDataSource.LoadRangeParams(startPosition, count), callback);
        }
    }

    /**
     * Load initial list data.
     * <p>
     * This method is called to load the initial page(s) from the DRxDataSource.
     * <p>
     * Result list must be a multiple of pageSize to enable efficient tiling.
     *
     * @param params Parameters for initial load, including requested start position, load size, and
     *               page size.
     * @param callback Callback that receives initial load data, including
     *                 position and total data set size.
     */
    @WorkerThread
    public abstract void loadInitial(
            @NonNull DRxPositionalDataSource.LoadInitialParams params,
            @NonNull DRxPositionalDataSource.LoadInitialCallback<T> callback);

    /**
     * Called to load a range of data from the DRxDataSource.
     * <p>
     * This method is called to load additional pages from the DRxDataSource after the
     * LoadInitialCallback passed to dispatchLoadInitial has initialized a DRxPagedList.
     * <p>
     * Unlike {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)}, this method must return
     * the number of items requested, at the position requested.
     *
     * @param params Parameters for load, including start position and load size.
     * @param callback Callback that receives loaded data.
     */
    @WorkerThread
    public abstract void loadRange(@NonNull DRxPositionalDataSource.LoadRangeParams params,
                                   @NonNull DRxPositionalDataSource.LoadRangeCallback<T> callback);

    @Override
    boolean isContiguous() {
        return false;
    }

    @NonNull
    DRxContiguousDataSource<Integer, T> wrapAsContiguousWithoutPlaceholders() {
        return new DRxPositionalDataSource.ContiguousWithoutPlaceholdersWrapper<>(this);
    }

    /**
     * Helper for computing an initial position in
     * {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)} when total data set size can be
     * computed ahead of loading.
     * <p>
     * The value computed by this function will do bounds checking, page alignment, and positioning
     * based on initial load size requested.
     * <p>
     * Example usage in a DRxPositionalDataSource subclass:
     * <pre>
     * class ItemDataSourceDRx extends DRxPositionalDataSource&lt;Item> {
     *     private int computeCount() {
     *         // actual count code here
     *     }
     *
     *     private List&lt;Item> loadRangeInternal(int startPosition, int loadCount) {
     *         // actual load code here
     *     }
     *
     *     {@literal @}Override
     *     public void loadInitial({@literal @}NonNull LoadInitialParams params,
     *             {@literal @}NonNull LoadInitialCallback&lt;Item> callback) {
     *         int totalCount = computeCount();
     *         int position = computeInitialLoadPosition(params, totalCount);
     *         int loadSize = computeInitialLoadSize(params, position, totalCount);
     *         callback.onResult(loadRangeInternal(position, loadSize), position, totalCount);
     *     }
     *
     *     {@literal @}Override
     *     public void loadRange({@literal @}NonNull LoadRangeParams params,
     *             {@literal @}NonNull LoadRangeCallback&lt;Item> callback) {
     *         callback.onResult(loadRangeInternal(params.startPosition, params.loadSize));
     *     }
     * }</pre>
     *
     * @param params Params passed to {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)},
     *               including page size, and requested start/loadSize.
     * @param totalCount Total size of the data set.
     * @return Position to start loading at.
     *
     * @see #computeInitialLoadSize(DRxPositionalDataSource.LoadInitialParams, int, int)
     */
    public static int computeInitialLoadPosition(@NonNull DRxPositionalDataSource.LoadInitialParams params,
                                                 int totalCount) {
        int position = params.requestedStartPosition;
        int initialLoadSize = params.requestedLoadSize;
        int pageSize = params.pageSize;

        int pageStart = position / pageSize * pageSize;

        // maximum start pos is that which will encompass end of list
        int maximumLoadPage = ((totalCount - initialLoadSize + pageSize - 1) / pageSize) * pageSize;
        pageStart = Math.min(maximumLoadPage, pageStart);

        // minimum start position is 0
        pageStart = Math.max(0, pageStart);

        return pageStart;
    }

    /**
     * Helper for computing an initial load size in
     * {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)} when total data set size can be
     * computed ahead of loading.
     * <p>
     * This function takes the requested load size, and bounds checks it against the value returned
     * by {@link #computeInitialLoadPosition(DRxPositionalDataSource.LoadInitialParams, int)}.
     * <p>
     * Example usage in a DRxPositionalDataSource subclass:
     * <pre>
     * class ItemDataSourceDRx extends DRxPositionalDataSource&lt;Item> {
     *     private int computeCount() {
     *         // actual count code here
     *     }
     *
     *     private List&lt;Item> loadRangeInternal(int startPosition, int loadCount) {
     *         // actual load code here
     *     }
     *
     *     {@literal @}Override
     *     public void loadInitial({@literal @}NonNull LoadInitialParams params,
     *             {@literal @}NonNull LoadInitialCallback&lt;Item> callback) {
     *         int totalCount = computeCount();
     *         int position = computeInitialLoadPosition(params, totalCount);
     *         int loadSize = computeInitialLoadSize(params, position, totalCount);
     *         callback.onResult(loadRangeInternal(position, loadSize), position, totalCount);
     *     }
     *
     *     {@literal @}Override
     *     public void loadRange({@literal @}NonNull LoadRangeParams params,
     *             {@literal @}NonNull LoadRangeCallback&lt;Item> callback) {
     *         callback.onResult(loadRangeInternal(params.startPosition, params.loadSize));
     *     }
     * }</pre>
     *
     * @param params Params passed to {@link #loadInitial(DRxPositionalDataSource.LoadInitialParams, DRxPositionalDataSource.LoadInitialCallback)},
     *               including page size, and requested start/loadSize.
     * @param initialLoadPosition Value returned by
     *                          {@link #computeInitialLoadPosition(DRxPositionalDataSource.LoadInitialParams, int)}
     * @param totalCount Total size of the data set.
     * @return Number of items to load.
     *
     * @see #computeInitialLoadPosition(DRxPositionalDataSource.LoadInitialParams, int)
     */
    @SuppressWarnings("WeakerAccess")
    public static int computeInitialLoadSize(@NonNull DRxPositionalDataSource.LoadInitialParams params,
                                             int initialLoadPosition, int totalCount) {
        return Math.min(totalCount - initialLoadPosition, params.requestedLoadSize);
    }

    @SuppressWarnings("deprecation")
    static class ContiguousWithoutPlaceholdersWrapper<Value>
            extends DRxContiguousDataSource<Integer, Value> {
        @NonNull
        final DRxPositionalDataSource<Value> mSource;

        ContiguousWithoutPlaceholdersWrapper(
                @NonNull DRxPositionalDataSource<Value> source) {
            mSource = source;
        }

        @Override
        public void addInvalidatedCallback(
                @NonNull InvalidatedCallback onInvalidatedCallback) {
            mSource.addInvalidatedCallback(onInvalidatedCallback);
        }

        @Override
        public void removeInvalidatedCallback(
                @NonNull InvalidatedCallback onInvalidatedCallback) {
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

        @NonNull
        @Override
        public <ToValue> DRxDataSource<Integer, ToValue> mapByPage(
                @NonNull Function<List<Value>, List<ToValue>> function) {
            throw new UnsupportedOperationException(
                    "Inaccessible inner type doesn't support map op");
        }

        @NonNull
        @Override
        public <ToValue> DRxDataSource<Integer, ToValue> map(
                @NonNull Function<Value, ToValue> function) {
            throw new UnsupportedOperationException(
                    "Inaccessible inner type doesn't support map op");
        }

        @Override
        void dispatchLoadInitial(@Nullable Integer position, int initialLoadSize, int pageSize,
                                 boolean enablePlaceholders, @NonNull Executor mainThreadExecutor,
                                 @NonNull DRxPageResult.Receiver<Value> receiver) {
            final int convertPosition = position == null ? 0 : position;

            // Note enablePlaceholders will be false here, but we don't have a way to communicate
            // this to DRxPositionalDataSource. This is fine, because only the list and its position
            // offset will be consumed by the LoadInitialCallback.
            mSource.dispatchLoadInitial(false, convertPosition, initialLoadSize,
                    pageSize, mainThreadExecutor, receiver);
        }

        @Override
        void dispatchLoadAfter(int currentEndIndex, @NonNull Value currentEndItem, int pageSize,
                               @NonNull Executor mainThreadExecutor,
                               @NonNull DRxPageResult.Receiver<Value> receiver) {
            int startIndex = currentEndIndex + 1;
            mSource.dispatchLoadRange(
                    DRxPageResult.APPEND, startIndex, pageSize, mainThreadExecutor, receiver);
        }

        @Override
        void dispatchLoadBefore(int currentBeginIndex, @NonNull Value currentBeginItem,
                                int pageSize, @NonNull Executor mainThreadExecutor,
                                @NonNull DRxPageResult.Receiver<Value> receiver) {
            int startIndex = currentBeginIndex - 1;
            if (startIndex < 0) {
                // trigger empty list load
                mSource.dispatchLoadRange(
                        DRxPageResult.PREPEND, startIndex, 0, mainThreadExecutor, receiver);
            } else {
                int loadSize = Math.min(pageSize, startIndex + 1);
                startIndex = startIndex - loadSize + 1;
                mSource.dispatchLoadRange(
                        DRxPageResult.PREPEND, startIndex, loadSize, mainThreadExecutor, receiver);
            }
        }

        @Override
        Integer getKey(int position, Value item) {
            return position;
        }

    }

    @NonNull
    @Override
    public final <V> DRxPositionalDataSource<V> mapByPage(
            @NonNull Function<List<T>, List<V>> function) {
        return new DRxWrapperPositionalDataSource<>(this, function);
    }

    @NonNull
    @Override
    public final <V> DRxPositionalDataSource<V> map(@NonNull Function<T, V> function) {
        return mapByPage(createListFunction(function));
    }
}

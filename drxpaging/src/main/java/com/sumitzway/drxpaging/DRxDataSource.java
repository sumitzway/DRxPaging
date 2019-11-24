package com.sumitzway.drxpaging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;


@SuppressWarnings("unused") // suppress warning to remove Key/Value, needed for subclass type safety
public abstract class DRxDataSource<Key, Value> {
    /**
     * Factory for DataSources.
     * <p>
     * Data-loading systems of an application or library can implement this interface to allow
     * {@code LiveData<DRxPagedList>}s to be created. For example, Room can provide a
     * DRxDataSource.Factory for a given SQL query:
     *
     * <pre>
     * {@literal @}Dao
     * interface UserDao {
     *    {@literal @}Query("SELECT * FROM user ORDER BY lastName ASC")
     *    public abstract DRxDataSource.Factory&lt;Integer, User> usersByLastName();
     * }
     * </pre>
     * In the above sample, {@code Integer} is used because it is the {@code Key} type of
     * DRxPositionalDataSource. Currently, Room uses the {@code LIMIT}/{@code OFFSET} SQL keywords to
     * page a large query with a DRxPositionalDataSource.
     *
     * @param <Key>   Key identifying items in DRxDataSource.
     * @param <Value> Type of items in the list loaded by the DataSources.
     */
    public abstract static class Factory<Key, Value> {

        @NonNull
        public abstract DRxDataSource<Key, Value> create();

        /**
         * Applies the given function to each value emitted by DataSources produced by this Factory.
         * <p>
         * Same as {@link #mapByPage(Function)}, but operates on individual items.
         *
         * @param function  Function that runs on each loaded item, returning items of a potentially
         *                  new type.
         * @param <ToValue> Type of items produced by the new DRxDataSource, from the passed function.
         * @return A new DRxDataSource.Factory, which transforms items using the given function.
         * @see #mapByPage(Function)
         * @see DRxDataSource#map(Function)
         * @see DRxDataSource#mapByPage(Function)
         */
        @NonNull
        public <ToValue> DRxDataSource.Factory<Key, ToValue> map(
                @NonNull Function<Value, ToValue> function) {
            return mapByPage(createListFunction(function));
        }

        /**
         * Applies the given function to each value emitted by DataSources produced by this Factory.
         * <p>
         * Same as {@link #map(Function)}, but allows for batch conversions.
         *
         * @param function  Function that runs on each loaded page, returning items of a potentially
         *                  new type.
         * @param <ToValue> Type of items produced by the new DRxDataSource, from the passed function.
         * @return A new DRxDataSource.Factory, which transforms items using the given function.
         * @see #map(Function)
         * @see DRxDataSource#map(Function)
         * @see DRxDataSource#mapByPage(Function)
         */
        @NonNull
        public <ToValue> DRxDataSource.Factory<Key, ToValue> mapByPage(
                @NonNull final Function<List<Value>, List<ToValue>> function) {
            return new DRxDataSource.Factory<Key, ToValue>() {
                @Override
                public DRxDataSource<Key, ToValue> create() {
                    return DRxDataSource.Factory.this.create().mapByPage(function);
                }
            };
        }
    }

    @NonNull
    static <X, Y> Function<List<X>, List<Y>> createListFunction(
            final @NonNull Function<X, Y> innerFunc) {
        return new Function<List<X>, List<Y>>() {
            @Override
            public List<Y> apply(@NonNull List<X> source) {
                List<Y> out = new ArrayList<>(source.size());
                for (int i = 0; i < source.size(); i++) {
                    out.add(innerFunc.apply(source.get(i)));
                }
                return out;
            }
        };
    }

    static <A, B> List<B> convert(Function<List<A>, List<B>> function, List<A> source) {
        List<B> dest = function.apply(source);
        if (dest.size() != source.size()) {
            throw new IllegalStateException("Invalid Function " + function
                    + " changed return size. This is not supported.");
        }
        return dest;
    }

    // Since we currently rely on implementation details of two implementations,
    // prevent external subclassing, except through exposed subclasses
    DRxDataSource() {
    }

    /**
     * Applies the given function to each value emitted by the DRxDataSource.
     * <p>
     * Same as {@link #map(Function)}, but allows for batch conversions.
     *
     * @param function  Function that runs on each loaded page, returning items of a potentially
     *                  new type.
     * @param <ToValue> Type of items produced by the new DRxDataSource, from the passed function.
     * @return A new DRxDataSource, which transforms items using the given function.
     * @see #map(Function)
     * @see DRxDataSource.Factory#map(Function)
     * @see DRxDataSource.Factory#mapByPage(Function)
     */
    @NonNull
    public abstract <ToValue> DRxDataSource<Key, ToValue> mapByPage(
            @NonNull Function<List<Value>, List<ToValue>> function);

    /**
     * Applies the given function to each value emitted by the DRxDataSource.
     * <p>
     * Same as {@link #mapByPage(Function)}, but operates on individual items.
     *
     * @param function  Function that runs on each loaded item, returning items of a potentially
     *                  new type.
     * @param <ToValue> Type of items produced by the new DRxDataSource, from the passed function.
     * @return A new DRxDataSource, which transforms items using the given function.
     * @see #mapByPage(Function)
     * @see DRxDataSource.Factory#map(Function)
     * @see DRxDataSource.Factory#mapByPage(Function)
     */
    @NonNull
    public abstract <ToValue> DRxDataSource<Key, ToValue> map(
            @NonNull Function<Value, ToValue> function);

    /**
     * Returns true if the data source guaranteed to produce a contiguous set of items,
     * never producing gaps.
     */
    abstract boolean isContiguous();

    static class LoadCallbackHelper<T> {
        static void validateInitialLoadParams(@NonNull List<?> data, int position, int totalCount) {
            if (position < 0) {
                throw new IllegalArgumentException("Position must be non-negative");
            }
            if (data.size() + position > totalCount) {
                throw new IllegalArgumentException(
                        "List size + position too large, last item in list beyond totalCount.");
            }
            if (data.size() == 0 && totalCount > 0) {
                throw new IllegalArgumentException(
                        "Initial result cannot be empty if items are present in data set.");
            }
        }

        @DRxPageResult.ResultType
        final int mResultType;
        private final DRxDataSource mDataSource;
        final DRxPageResult.Receiver<T> mReceiver;

        // mSignalLock protects mPostExecutor, and mHasSignalled
        private final Object mSignalLock = new Object();
        private Executor mPostExecutor = null;
        private boolean mHasSignalled = false;

        LoadCallbackHelper(@NonNull DRxDataSource dataSource, @DRxPageResult.ResultType int resultType,
                           @Nullable Executor mainThreadExecutor, @NonNull DRxPageResult.Receiver<T> receiver) {
            mDataSource = dataSource;
            mResultType = resultType;
            mPostExecutor = mainThreadExecutor;
            mReceiver = receiver;
        }

        void setPostExecutor(Executor postExecutor) {
            synchronized (mSignalLock) {
                mPostExecutor = postExecutor;
            }
        }

        /**
         * Call before verifying args, or dispatching actul results
         *
         * @return true if DRxDataSource was invalid, and invalid result dispatched
         */
        boolean dispatchInvalidResultIfInvalid() {
            if (mDataSource.isInvalid()) {
                dispatchResultToReceiver(DRxPageResult.<T>getInvalidResult());
                return true;
            }
            return false;
        }

        void dispatchResultToReceiver(final @NonNull DRxPageResult<T> result) {
            Executor executor;
            synchronized (mSignalLock) {
                if (mHasSignalled) {
//                    throw new IllegalStateException(
//                            "callback.onResult already called, cannot call again.");
                }
                mHasSignalled = true;
                executor = mPostExecutor;
            }

            if (executor != null) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mReceiver.onPageResult(mResultType, result);
                    }
                });
            } else {
                mReceiver.onPageResult(mResultType, result);
            }
        }
    }

    /**
     * Invalidation callback for DRxDataSource.
     * <p>
     * Used to signal when a DRxDataSource a data source has become invalid, and that a new data source
     * is needed to continue loading data.
     */
    public interface InvalidatedCallback {
        /**
         * Called when the data backing the list has become invalid. This callback is typically used
         * to signal that a new data source is needed.
         * <p>
         * This callback will be invoked on the thread that calls {@link #invalidate()}. It is valid
         * for the data source to invalidate itself during its load methods, or for an outside
         * source to invalidate it.
         */
        @AnyThread
        void onInvalidated();
    }

    private AtomicBoolean mInvalid = new AtomicBoolean(false);

    private CopyOnWriteArrayList<InvalidatedCallback> mOnInvalidatedCallbacks =
            new CopyOnWriteArrayList<>();

    /**
     * Add a callback to invoke when the DRxDataSource is first invalidated.
     * <p>
     * Once invalidated, a data source will not become valid again.
     * <p>
     * A data source will only invoke its callbacks once - the first time {@link #invalidate()}
     * is called, on that thread.
     *
     * @param onInvalidatedCallback The callback, will be invoked on thread that
     *                              {@link #invalidate()} is called on.
     */
    @AnyThread
    @SuppressWarnings("WeakerAccess")
    public void addInvalidatedCallback(@NonNull DRxDataSource.InvalidatedCallback onInvalidatedCallback) {
        mOnInvalidatedCallbacks.add(onInvalidatedCallback);
    }

    /**
     * Remove a previously added invalidate callback.
     *
     * @param onInvalidatedCallback The previously added callback.
     */
    @AnyThread
    @SuppressWarnings("WeakerAccess")
    public void removeInvalidatedCallback(@NonNull DRxDataSource.InvalidatedCallback onInvalidatedCallback) {
        mOnInvalidatedCallbacks.remove(onInvalidatedCallback);
    }

    /**
     * Signal the data source to stop loading, and notify its callback.
     * <p>
     * If invalidate has already been called, this method does nothing.
     */
    @AnyThread
    public void invalidate() {
        if (mInvalid.compareAndSet(false, true)) {
            for (DRxDataSource.InvalidatedCallback callback : mOnInvalidatedCallbacks) {
                callback.onInvalidated();
            }
        }
    }

    /**
     * Returns true if the data source is invalid, and can no longer be queried for data.
     *
     * @return True if the data source is invalid, and can no longer return data.
     */
    @WorkerThread
    public boolean isInvalid() {
        return mInvalid.get();
    }
}

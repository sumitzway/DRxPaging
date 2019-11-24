package com.sumitzway.drxpaging;


import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class DRxPagedList<T> extends DRxAbstractList<T> {

    @NonNull
    final Executor mMainThreadExecutor;
    @NonNull
    final Executor mBackgroundThreadExecutor;
    @Nullable
    final DRxPagedList.BoundaryCallback<T> mBoundaryCallback;
    @NonNull
    final DRxPagedList.Config mConfig;
    @NonNull
    final DRxPagedStorage<T> mStorage;

    /**
     * Last access location, in total position space (including offset).
     * <p>
     * Used by positional data
     * sources to initialize loading near viewport
     */
    int mLastLoad = 0;
    T mLastItem = null;

    final int mRequiredRemainder;

    // if set to true, mBoundaryCallback is non-null, and should
    // be dispatched when nearby load has occurred
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            boolean mBoundaryCallbackBeginDeferred = false;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            boolean mBoundaryCallbackEndDeferred = false;

    // lowest and highest index accessed by loadAround. Used to
    // decide when mBoundaryCallback should be dispatched
    private int mLowestIndexAccessed = Integer.MAX_VALUE;
    private int mHighestIndexAccessed = Integer.MIN_VALUE;

    private final AtomicBoolean mDetached = new AtomicBoolean(false);

    private final ArrayList<WeakReference<Callback>> mCallbacks = new ArrayList<>();

    DRxPagedList(@NonNull DRxPagedStorage<T> storage,
                 @NonNull Executor mainThreadExecutor,
                 @NonNull Executor backgroundThreadExecutor,
                 @Nullable DRxPagedList.BoundaryCallback<T> boundaryCallback,
                 @NonNull DRxPagedList.Config config) {
        mStorage = storage;
        mMainThreadExecutor = mainThreadExecutor;
        mBackgroundThreadExecutor = backgroundThreadExecutor;
        mBoundaryCallback = boundaryCallback;
        mConfig = config;
        mRequiredRemainder = mConfig.prefetchDistance * 2 + mConfig.pageSize;
    }

    /**
     * Create a DRxPagedList which loads data from the provided data source on a background thread,
     * posting updates to the main thread.
     *
     * @param dataSource       DRxDataSource providing data to the DRxPagedList
     * @param notifyExecutor   Thread that will use and consume data from the DRxPagedList.
     *                         Generally, this is the UI/main thread.
     * @param fetchExecutor    Data loading will be done via this executor -
     *                         should be a background thread.
     * @param boundaryCallback Optional boundary callback to attach to the list.
     * @param config           DRxPagedList Config, which defines how the DRxPagedList will load data.
     * @param <K>              Key type that indicates to the DRxDataSource what data to load.
     * @param <T>              Type of items to be held and loaded by the DRxPagedList.
     * @return Newly created DRxPagedList, which will page in data from the DRxDataSource as needed.
     */
    @NonNull
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static <K, T> DRxPagedList<T> create(@NonNull DRxDataSource<K, T> dataSource,
                                         @NonNull Executor notifyExecutor,
                                         @NonNull Executor fetchExecutor,
                                         @Nullable DRxPagedList.BoundaryCallback<T> boundaryCallback,
                                         @NonNull DRxPagedList.Config config,
                                         @Nullable K key) {
        if (dataSource.isContiguous() || !config.enablePlaceholders) {
            int lastLoad = DRxContiguousPagedList.LAST_LOAD_UNSPECIFIED;
            if (!dataSource.isContiguous()) {
                //noinspection unchecked
                dataSource = (DRxDataSource<K, T>) ((DRxPositionalDataSource<T>) dataSource)
                        .wrapAsContiguousWithoutPlaceholders();
                if (key != null) {
                    lastLoad = (Integer) key;
                }
            }
            DRxContiguousDataSource<K, T> contigDataSource = (DRxContiguousDataSource<K, T>) dataSource;
            return new DRxContiguousPagedList<>(contigDataSource,
                    notifyExecutor,
                    fetchExecutor,
                    boundaryCallback,
                    config,
                    key,
                    lastLoad);
        } else {
            return new DRxTiledPagedList<>((DRxPositionalDataSource<T>) dataSource,
                    notifyExecutor,
                    fetchExecutor,
                    boundaryCallback,
                    config,
                    (key != null) ? (Integer) key : 0);
        }
    }

    /**
     * Builder class for DRxPagedList.
     * <p>
     * DRxDataSource, Config, main thread and background executor must all be provided.
     * <p>
     * A DRxPagedList queries initial data from its DRxDataSource during construction, to avoid empty
     * PagedLists being presented to the UI when possible. It's preferred to present initial data,
     * so that the UI doesn't show an empty list, or placeholders for a few frames, just before
     * showing initial content.
     * <p>
     * {@link DRxLivePagedListBuilder} does this creation on a background thread automatically, if you
     * want to receive a {@code LiveData<DRxPagedList<...>>}.
     *
     * @param <Key>   Type of key used to load data from the DRxDataSource.
     * @param <Value> Type of items held and loaded by the DRxPagedList.
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder<Key, Value> {
        private final DRxDataSource<Key, Value> mDataSource;
        private final DRxPagedList.Config mConfig;
        private Executor mNotifyExecutor;
        private Executor mFetchExecutor;
        private DRxPagedList.BoundaryCallback mBoundaryCallback;
        private Key mInitialKey;

        /**
         * Create a DRxPagedList.Builder with the provided {@link DRxDataSource} and {@link DRxPagedList.Config}.
         *
         * @param dataSource DRxDataSource the DRxPagedList will load from.
         * @param config     Config that defines how the DRxPagedList loads data from its DRxDataSource.
         */
        public Builder(@NonNull DRxDataSource<Key, Value> dataSource, @NonNull DRxPagedList.Config config) {
            //noinspection ConstantConditions
            if (dataSource == null) {
                throw new IllegalArgumentException("DRxDataSource may not be null");
            }
            //noinspection ConstantConditions
            if (config == null) {
                throw new IllegalArgumentException("Config may not be null");
            }
            mDataSource = dataSource;
            mConfig = config;
        }

        /**
         * Create a DRxPagedList.Builder with the provided {@link DRxDataSource} and page size.
         * <p>
         * This method is a convenience for:
         * <pre>
         * DRxPagedList.Builder(dataSource,
         *         new DRxPagedList.Config.Builder().setPageSize(pageSize).build());
         * </pre>
         *
         * @param dataSource DRxDataSource the DRxPagedList will load from.
         * @param pageSize   Config that defines how the DRxPagedList loads data from its DRxDataSource.
         */
        public Builder(@NonNull DRxDataSource<Key, Value> dataSource, int pageSize) {
            this(dataSource, new DRxPagedList.Config.Builder().setPageSize(pageSize).build());
        }

        /**
         * The executor defining where page loading updates are dispatched.
         *
         * @param notifyExecutor Executor that receives DRxPagedList updates, and where
         *                       {@link DRxPagedList.Callback} calls are dispatched. Generally, this is the ui/main thread.
         * @return this
         */
        @NonNull
        public DRxPagedList.Builder<Key, Value> setNotifyExecutor(@NonNull Executor notifyExecutor) {
            mNotifyExecutor = notifyExecutor;
            return this;
        }

        /**
         * The executor used to fetch additional pages from the DRxDataSource.
         * <p>
         * Does not affect initial load, which will be done immediately on whichever thread the
         * DRxPagedList is created on.
         *
         * @param fetchExecutor Executor used to fetch from DataSources, generally a background
         *                      thread pool for e.g. I/O or network loading.
         * @return this
         */
        @NonNull
        public DRxPagedList.Builder<Key, Value> setFetchExecutor(@NonNull Executor fetchExecutor) {
            mFetchExecutor = fetchExecutor;
            return this;
        }

        /**
         * The BoundaryCallback for out of data events.
         * <p>
         * Pass a BoundaryCallback to listen to when the DRxPagedList runs out of data to load.
         *
         * @param boundaryCallback BoundaryCallback for listening to out-of-data events.
         * @return this
         */
        @SuppressWarnings("unused")
        @NonNull
        public DRxPagedList.Builder<Key, Value> setBoundaryCallback(
                @Nullable DRxPagedList.BoundaryCallback boundaryCallback) {
            mBoundaryCallback = boundaryCallback;
            return this;
        }

        /**
         * Sets the initial key the DRxDataSource should load around as part of initialization.
         *
         * @param initialKey Key the DRxDataSource should load around as part of initialization.
         * @return this
         */
        @NonNull
        public DRxPagedList.Builder<Key, Value> setInitialKey(@Nullable Key initialKey) {
            mInitialKey = initialKey;
            return this;
        }

        /**
         * Creates a {@link DRxPagedList} with the given parameters.
         * <p>
         * This call will dispatch the {@link DRxDataSource}'s loadInitial method immediately. If a
         * DRxDataSource posts all of its work (e.g. to a network thread), the DRxPagedList will
         * be immediately created as empty, and grow to its initial size when the initial load
         * completes.
         * <p>
         * If the DRxDataSource implements its load synchronously, doing the load work immediately in
         * the loadInitial method, the DRxPagedList will block on that load before completing
         * construction. In this case, use a background thread to create a DRxPagedList.
         * <p>
         * It's fine to create a DRxPagedList with an async DRxDataSource on the main thread, such as in
         * the constructor of a ViewModel. An async network load won't block the initialLoad
         * function. For a synchronous DRxDataSource such as one created from a Room database, a
         * {@code LiveData<DRxPagedList>} can be safely constructed with {@link DRxLivePagedListBuilder}
         * on the main thread, since actual construction work is deferred, and done on a background
         * thread.
         * <p>
         * While build() will always return a DRxPagedList, it's important to note that the DRxPagedList
         * initial load may fail to acquire data from the DRxDataSource. This can happen for example if
         * the DRxDataSource is invalidated during its initial load. If this happens, the DRxPagedList
         * will be immediately {@link DRxPagedList#isDetached() detached}, and you can retry
         * construction (including setting a new DRxDataSource).
         *
         * @return The newly constructed DRxPagedList
         */
        @WorkerThread
        @NonNull
        public DRxPagedList<Value> build() {
            // TODO: define defaults, once they can be used in module without android dependency
            if (mNotifyExecutor == null) {
                throw new IllegalArgumentException("MainThreadExecutor required");
            }
            if (mFetchExecutor == null) {
                throw new IllegalArgumentException("BackgroundThreadExecutor required");
            }

            //noinspection unchecked
            return DRxPagedList.create(
                    mDataSource,
                    mNotifyExecutor,
                    mFetchExecutor,
                    mBoundaryCallback,
                    mConfig,
                    mInitialKey);
        }
    }

    /**
     * Get the item in the list of loaded items at the provided index.
     *
     * @param index Index in the loaded item list. Must be >= 0, and &lt; {@link #size()}
     * @return The item at the passed index, or null if a null placeholder is at the specified
     * position.
     * @see #size()
     */
    @Override
    @Nullable
    public T get(int index) {
        T item = mStorage.get(index);
        if (item != null) {
            mLastItem = item;
        }
        return item;
    }

    /**
     * Load adjacent items to passed index.
     *
     * @param index Index at which to load.
     */
    public void loadAround(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
        }

        mLastLoad = index + getPositionOffset();
        loadAroundInternal(index);

        mLowestIndexAccessed = Math.min(mLowestIndexAccessed, index);
        mHighestIndexAccessed = Math.max(mHighestIndexAccessed, index);

        /*
         * mLowestIndexAccessed / mHighestIndexAccessed have been updated, so check if we need to
         * dispatch boundary callbacks. Boundary callbacks are deferred until last items are loaded,
         * and accesses happen near the boundaries.
         *
         * Note: we post here, since RecyclerView may want to add items in response, and this
         * call occurs in DRxPagedListAdapter bind.
         */
        tryDispatchBoundaryCallbacks(true);
    }

    // Creation thread for initial synchronous load, otherwise main thread
    // Safe to access main thread only state - no other thread has reference during construction
    @AnyThread
    void deferBoundaryCallbacks(final boolean deferEmpty,
                                final boolean deferBegin, final boolean deferEnd) {
        if (mBoundaryCallback == null) {
            throw new IllegalStateException("Can't defer BoundaryCallback, no instance");
        }

        /*
         * If lowest/highest haven't been initialized, set them to storage size,
         * since placeholders must already be computed by this point.
         *
         * This is just a minor optimization so that BoundaryCallback callbacks are sent immediately
         * if the initial load size is smaller than the prefetch window (see
         * TiledPagedListTest#boundaryCallback_immediate())
         */
        if (mLowestIndexAccessed == Integer.MAX_VALUE) {
            mLowestIndexAccessed = mStorage.size();
        }
        if (mHighestIndexAccessed == Integer.MIN_VALUE) {
            mHighestIndexAccessed = 0;
        }

        if (deferEmpty || deferBegin || deferEnd) {
            // Post to the main thread, since we may be on creation thread currently
            mMainThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // on is dispatched immediately, since items won't be accessed
                    //noinspection ConstantConditions
                    if (deferEmpty) {
                        mBoundaryCallback.onZeroItemsLoaded();
                    }

                    // for other callbacks, mark deferred, and only dispatch if loadAround
                    // has been called near to the position
                    if (deferBegin) {
                        mBoundaryCallbackBeginDeferred = true;
                    }
                    if (deferEnd) {
                        mBoundaryCallbackEndDeferred = true;
                    }
                    tryDispatchBoundaryCallbacks(false);
                }
            });
        }
    }

    /**
     * Call this when mLowest/HighestIndexAccessed are changed, or
     * mBoundaryCallbackBegin/EndDeferred is set.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void tryDispatchBoundaryCallbacks(boolean post) {
        final boolean dispatchBegin = mBoundaryCallbackBeginDeferred
                && mLowestIndexAccessed <= mConfig.prefetchDistance;
        final boolean dispatchEnd = mBoundaryCallbackEndDeferred
                && mHighestIndexAccessed >= size() - 1 - mConfig.prefetchDistance;

        if (!dispatchBegin && !dispatchEnd) {
            return;
        }

        if (dispatchBegin) {
            mBoundaryCallbackBeginDeferred = false;
        }
        if (dispatchEnd) {
            mBoundaryCallbackEndDeferred = false;
        }
        if (post) {
            mMainThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    dispatchBoundaryCallbacks(dispatchBegin, dispatchEnd);
                }
            });
        } else {
            dispatchBoundaryCallbacks(dispatchBegin, dispatchEnd);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void dispatchBoundaryCallbacks(boolean begin, boolean end) {
        // safe to deref mBoundaryCallback here, since we only defer if mBoundaryCallback present
        if (begin) {
            //noinspection ConstantConditions
            mBoundaryCallback.onItemAtFrontLoaded(mStorage.getFirstLoadedItem());
        }
        if (end) {
            //noinspection ConstantConditions
            mBoundaryCallback.onItemAtEndLoaded(mStorage.getLastLoadedItem());
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void offsetAccessIndices(int offset) {
        // update last loadAround index
        mLastLoad += offset;

        // update access range
        mLowestIndexAccessed += offset;
        mHighestIndexAccessed += offset;
    }

    /**
     * Returns size of the list, including any not-yet-loaded null padding.
     * <p>
     * To get the number of loaded items, not counting placeholders, use {@link #getLoadedCount()}.
     *
     * @return Current total size of the list, including placeholders.
     * @see #getLoadedCount()
     */
    @Override
    public int size() {
        return mStorage.size();
    }

    /**
     * Returns the number of items loaded in the DRxPagedList.
     * <p>
     * Unlike {@link #size()} this counts only loaded items, not placeholders.
     * <p>
     * If placeholders are {@link DRxPagedList.Config#enablePlaceholders disabled}, this method is equivalent to
     * {@link #size()}.
     *
     * @return Number of items currently loaded, not counting placeholders.
     * @see #size()
     */
    public int getLoadedCount() {
        return mStorage.getLoadedCount();
    }

    /**
     * Returns whether the list is immutable.
     * <p>
     * Immutable lists may not become mutable again, and may safely be accessed from any thread.
     * <p>
     * In the future, this method may return true when a DRxPagedList has completed loading from its
     * DRxDataSource. Currently, it is equivalent to {@link #isDetached()}.
     *
     * @return True if the DRxPagedList is immutable.
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isImmutable() {
        return isDetached();
    }

    /**
     * Returns an immutable snapshot of the DRxPagedList in its current state.
     * <p>
     * If this DRxPagedList {@link #isImmutable() is immutable} due to its DRxDataSource being invalid, it
     * will be returned.
     *
     * @return Immutable snapshot of DRxPagedList data.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public List<T> snapshot() {
        if (isImmutable()) {
            return this;
        }
        return new DRxSnapshotPagedList<>(this);
    }

    abstract boolean isContiguous();

    /**
     * Return the Config used to construct this DRxPagedList.
     *
     * @return the Config of this DRxPagedList
     */
    @NonNull
    public DRxPagedList.Config getConfig() {
        return mConfig;
    }

    /**
     * Return the DRxDataSource that provides data to this DRxPagedList.
     *
     * @return the DRxDataSource of this DRxPagedList.
     */
    @NonNull
    public abstract DRxDataSource<?, T> getDataSource();

    /**
     * Return the key for the position passed most recently to {@link #loadAround(int)}.
     * <p>
     * When a DRxPagedList is invalidated, you can pass the key returned by this function to initialize
     * the next DRxPagedList. This ensures (depending on load times) that the next DRxPagedList that
     * arrives will have data that overlaps. If you use {@link DRxLivePagedListBuilder}, it will do
     * this for you.
     *
     * @return Key of position most recently passed to {@link #loadAround(int)}.
     */
    @Nullable
    public abstract Object getLastKey();

    /**
     * True if the DRxPagedList has detached the DRxDataSource it was loading from, and will no longer
     * load new data.
     * <p>
     * A detached list is {@link #isImmutable() immutable}.
     *
     * @return True if the data source is detached.
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isDetached() {
        return mDetached.get();
    }

    /**
     * Detach the DRxPagedList from its DRxDataSource, and attempt to load no more data.
     * <p>
     * This is called automatically when a DRxDataSource load returns <code>null</code>, which is a
     * signal to stop loading. The DRxPagedList will continue to present existing data, but will not
     * initiate new loads.
     */
    @SuppressWarnings("WeakerAccess")
    public void detach() {
        mDetached.set(true);
    }


    public int getPositionOffset() {
        return mStorage.getPositionOffset();
    }


    @SuppressWarnings("WeakerAccess")
    public void addWeakCallback(@Nullable List<T> previousSnapshot, @NonNull DRxPagedList.Callback callback) {
        if (previousSnapshot != null && previousSnapshot != this) {

            if (previousSnapshot.isEmpty()) {
                if (!mStorage.isEmpty()) {
                    // If snapshot is empty, diff is trivial - just notify number new items.
                    // Note: occurs in async init, when snapshot taken before init page arrives
                    callback.onInserted(0, mStorage.size());
                }
            } else {
                DRxPagedList<T> storageSnapshot = (DRxPagedList<T>) previousSnapshot;

                //noinspection unchecked
                dispatchUpdatesSinceSnapshot(storageSnapshot, callback);
            }
        }

        // first, clean up any empty weak refs
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            final DRxPagedList.Callback currentCallback = mCallbacks.get(i).get();
            if (currentCallback == null) {
                mCallbacks.remove(i);
            }
        }

        // then add the new one
        mCallbacks.add(new WeakReference<>(callback));
    }

    /**
     * Removes a previously added callback.
     *
     * @param callback Callback, previously added.
     * @see #addWeakCallback(List, DRxPagedList.Callback)
     */
    @SuppressWarnings("WeakerAccess")
    public void removeWeakCallback(@NonNull DRxPagedList.Callback callback) {
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            final DRxPagedList.Callback currentCallback = mCallbacks.get(i).get();
            if (currentCallback == null || currentCallback == callback) {
                // found callback, or empty weak ref
                mCallbacks.remove(i);
            }
        }
    }

    void notifyInserted(int position, int count) {
        if (count != 0) {
            for (int i = mCallbacks.size() - 1; i >= 0; i--) {
                final DRxPagedList.Callback callback = mCallbacks.get(i).get();
                if (callback != null) {
                    callback.onInserted(position, count);
                }
            }
        }
    }

    void notifyChanged(int position, int count) {
        if (count != 0) {
            for (int i = mCallbacks.size() - 1; i >= 0; i--) {
                final DRxPagedList.Callback callback = mCallbacks.get(i).get();

                if (callback != null) {
                    callback.onChanged(position, count);
                }
            }
        }
    }

    void notifyRemoved(int position, int count) {
        if (count != 0) {
            for (int i = mCallbacks.size() - 1; i >= 0; i--) {
                final DRxPagedList.Callback callback = mCallbacks.get(i).get();

                if (callback != null) {
                    callback.onRemoved(position, count);
                }
            }
        }
    }

    /**
     * Dispatch updates since the non-empty snapshot was taken.
     *
     * @param snapshot Non-empty snapshot.
     * @param callback Callback for updates that have occurred since snapshot.
     */
    abstract void dispatchUpdatesSinceSnapshot(@NonNull DRxPagedList<T> snapshot,
                                               @NonNull DRxPagedList.Callback callback);

    abstract void loadAroundInternal(int index);

    /**
     * Callback signaling when content is loaded into the list.
     * <p>
     * Can be used to listen to items being paged in and out. These calls will be dispatched on
     * the executor defined by {@link DRxPagedList.Builder#setNotifyExecutor(Executor)}, which is generally
     * the main/UI thread.
     */
    public abstract static class Callback {
        /**
         * Called when null padding items have been loaded to signal newly available data, or when
         * data that hasn't been used in a while has been dropped, and swapped back to null.
         *
         * @param position Position of first newly loaded items, out of total number of items
         *                 (including padded nulls).
         * @param count    Number of items loaded.
         */
        public abstract void onChanged(int position, int count);

        /**
         * Called when new items have been loaded at the end or beginning of the list.
         *
         * @param position Position of the first newly loaded item (in practice, either
         *                 <code>0</code> or <code>size - 1</code>.
         * @param count    Number of items loaded.
         */
        public abstract void onInserted(int position, int count);

        /**
         * Called when items have been removed at the end or beginning of the list, and have not
         * been replaced by padded nulls.
         *
         * @param position Position of the first newly loaded item (in practice, either
         *                 <code>0</code> or <code>size - 1</code>.
         * @param count    Number of items loaded.
         */
        @SuppressWarnings("unused")
        public abstract void onRemoved(int position, int count);
    }

    /**
     * Configures how a DRxPagedList loads content from its DRxDataSource.
     * <p>
     * Use a Config {@link DRxPagedList.Config.Builder} to construct and define custom loading behavior, such as
     * {@link DRxPagedList.Config.Builder#setPageSize(int)}, which defines number of items loaded at a time}.
     */
    public static class Config {
        /**
         * When {@link #maxSize} is set to {@code MAX_SIZE_UNBOUNDED}, the maximum number of items
         * loaded is unbounded, and pages will never be dropped.
         */
        @SuppressWarnings("WeakerAccess")
        public static final int MAX_SIZE_UNBOUNDED = Integer.MAX_VALUE;

        /**
         * Size of each page loaded by the DRxPagedList.
         */
        public final int pageSize;

        /**
         * Prefetch distance which defines how far ahead to load.
         * <p>
         * If this value is set to 50, the paged list will attempt to load 50 items in advance of
         * data that's already been accessed.
         *
         * @see DRxPagedList#loadAround(int)
         */
        @SuppressWarnings("WeakerAccess")
        public final int prefetchDistance;

        /**
         * Defines whether the DRxPagedList may display null placeholders, if the DRxDataSource provides
         * them.
         */
        @SuppressWarnings("WeakerAccess")
        public final boolean enablePlaceholders;

        /**
         * Defines the maximum number of items that may be loaded into this pagedList before pages
         * should be dropped.
         * <p>
         * {@link DRxPageKeyedDataSource} does not currently support dropping pages - when
         * loading from a {@code DRxPageKeyedDataSource}, this value is ignored.
         *
         * @see #MAX_SIZE_UNBOUNDED
         * @see DRxPagedList.Config.Builder#setMaxSize(int)
         */
        public final int maxSize;

        /**
         * Size hint for initial load of DRxPagedList, often larger than a regular page.
         */
        @SuppressWarnings("WeakerAccess")
        public final int initialLoadSizeHint;

        Config(int pageSize, int prefetchDistance,
               boolean enablePlaceholders, int initialLoadSizeHint, int maxSize) {
            this.pageSize = pageSize;
            this.prefetchDistance = prefetchDistance;
            this.enablePlaceholders = enablePlaceholders;
            this.initialLoadSizeHint = initialLoadSizeHint;
            this.maxSize = maxSize;
        }

        /**
         * Builder class for {@link DRxPagedList.Config}.
         * <p>
         * You must at minimum specify page size with {@link #setPageSize(int)}.
         */
        public static final class Builder {
            static final int DEFAULT_INITIAL_PAGE_MULTIPLIER = 3;

            private int mPageSize = -1;
            private int mPrefetchDistance = -1;
            private int mInitialLoadSizeHint = -1;
            private boolean mEnablePlaceholders = true;
            private int mMaxSize = MAX_SIZE_UNBOUNDED;

            /**
             * Defines the number of items loaded at once from the DRxDataSource.
             * <p>
             * Should be several times the number of visible items onscreen.
             * <p>
             * Configuring your page size depends on how your data is being loaded and used. Smaller
             * page sizes improve memory usage, latency, and avoid GC churn. Larger pages generally
             * improve loading throughput, to a point
             * (avoid loading more than 2MB from SQLite at once, since it incurs extra cost).
             * <p>
             * If you're loading data for very large, social-media style cards that take up most of
             * a screen, and your database isn't a bottleneck, 10-20 may make sense. If you're
             * displaying dozens of items in a tiled grid, which can present items during a scroll
             * much more quickly, consider closer to 100.
             *
             * @param pageSize Number of items loaded at once from the DRxDataSource.
             * @return this
             */
            @NonNull
            public DRxPagedList.Config.Builder setPageSize(@IntRange(from = 1) int pageSize) {
                if (pageSize < 1) {
                    throw new IllegalArgumentException("Page size must be a positive number");
                }
                mPageSize = pageSize;
                return this;
            }

            /**
             * Defines how far from the edge of loaded content an access must be to trigger further
             * loading.
             * <p>
             * Should be several times the number of visible items onscreen.
             * <p>
             * If not set, defaults to page size.
             * <p>
             * A value of 0 indicates that no list items will be loaded until they are specifically
             * requested. This is generally not recommended, so that users don't observe a
             * placeholder item (with placeholders) or end of list (without) while scrolling.
             *
             * @param prefetchDistance Distance the DRxPagedList should prefetch.
             * @return this
             */
            @NonNull
            public DRxPagedList.Config.Builder setPrefetchDistance(@IntRange(from = 0) int prefetchDistance) {
                mPrefetchDistance = prefetchDistance;
                return this;
            }


            @SuppressWarnings("SameParameterValue")
            @NonNull
            public DRxPagedList.Config.Builder setEnablePlaceholders(boolean enablePlaceholders) {
                mEnablePlaceholders = enablePlaceholders;
                return this;
            }

            /**
             * Defines how many items to load when first load occurs.
             * <p>
             * This value is typically larger than page size, so on first load data there's a large
             * enough range of content loaded to cover small scrolls.
             * <p>
             * When using a {@link DRxPositionalDataSource}, the initial load size will be coerced to
             * an integer multiple of pageSize, to enable efficient tiling.
             * <p>
             * If not set, defaults to three times page size.
             *
             * @param initialLoadSizeHint Number of items to load while initializing the DRxPagedList.
             * @return this
             */
            @SuppressWarnings("WeakerAccess")
            @NonNull
            public DRxPagedList.Config.Builder setInitialLoadSizeHint(@IntRange(from = 1) int initialLoadSizeHint) {
                mInitialLoadSizeHint = initialLoadSizeHint;
                return this;
            }

            /**
             * Defines how many items to keep loaded at once.
             * <p>
             * This can be used to cap the number of items kept in memory by dropping pages. This
             * value is typically many pages so old pages are cached in case the user scrolls back.
             * <p>
             * This value must be at least two times the
             * {@link #setPrefetchDistance(int)} prefetch distance} plus the
             * {@link #setPageSize(int) page size}). This constraint prevent loads from being
             * continuously fetched and discarded due to prefetching.
             * <p>
             * The max size specified here best effort, not a guarantee. In practice, if maxSize
             * is many times the page size, the number of items held by the DRxPagedList will not grow
             * above this number. Exceptions are made as necessary to guarantee:
             * <ul>
             * <li>Pages are never dropped until there are more than two pages loaded. Note that
             * a DRxDataSource may not be held strictly to
             * {@link DRxPagedList.Config#pageSize requested pageSize}, so two pages may be larger than
             * expected.
             * <li>Pages are never dropped if they are within a prefetch window (defined to be
             * {@code pageSize + (2 * prefetchDistance)}) of the most recent load.
             * </ul>
             * <p>
             * {@link DRxPageKeyedDataSource} does not currently support dropping pages - when
             * loading from a {@code DRxPageKeyedDataSource}, this value is ignored.
             * <p>
             * If not set, defaults to {@code MAX_SIZE_UNBOUNDED}, which disables page dropping.
             *
             * @param maxSize Maximum number of items to keep in memory, or
             *                {@code MAX_SIZE_UNBOUNDED} to disable page dropping.
             * @return this
             * @see DRxPagedList.Config#MAX_SIZE_UNBOUNDED
             * @see DRxPagedList.Config#maxSize
             */
            @NonNull
            public DRxPagedList.Config.Builder setMaxSize(@IntRange(from = 2) int maxSize) {
                mMaxSize = maxSize;
                return this;
            }

            /**
             * Creates a {@link DRxPagedList.Config} with the given parameters.
             *
             * @return A new Config.
             */
            @NonNull
            public DRxPagedList.Config build() {
                if (mPrefetchDistance < 0) {
                    mPrefetchDistance = mPageSize;
                }
                if (mInitialLoadSizeHint < 0) {
                    mInitialLoadSizeHint = mPageSize * DEFAULT_INITIAL_PAGE_MULTIPLIER;
                }
                if (!mEnablePlaceholders && mPrefetchDistance == 0) {
                    throw new IllegalArgumentException("Placeholders and prefetch are the only ways"
                            + " to trigger loading of more data in the DRxPagedList, so either"
                            + " placeholders must be enabled, or prefetch distance must be > 0.");
                }
                if (mMaxSize != MAX_SIZE_UNBOUNDED) {
                    if (mMaxSize < mPageSize + mPrefetchDistance * 2) {
                        throw new IllegalArgumentException("Maximum size must be at least"
                                + " pageSize + 2*prefetchDist, pageSize=" + mPageSize
                                + ", prefetchDist=" + mPrefetchDistance + ", maxSize=" + mMaxSize);
                    }
                }

                return new DRxPagedList.Config(mPageSize, mPrefetchDistance,
                        mEnablePlaceholders, mInitialLoadSizeHint, mMaxSize);
            }
        }
    }

    /**
     * Signals when a DRxPagedList has reached the end of available data.
     * <p>
     * When local storage is a cache of network data, it's common to set up a streaming pipeline:
     * Network data is paged into the database, database is paged into UI. Paging from the database
     * to UI can be done with a {@code LiveData<DRxPagedList>}, but it's still necessary to know when
     * to trigger network loads.
     * <p>
     * BoundaryCallback does this signaling - when a DRxDataSource runs out of data at the end of
     * the list, {@link #onItemAtEndLoaded(Object)} is called, and you can start an async network
     * load that will write the result directly to the database. Because the database is being
     * observed, the UI bound to the {@code LiveData<DRxPagedList>} will update automatically to
     * account for the new items.
     * <p>
     * Note that a BoundaryCallback instance shared across multiple PagedLists (e.g. when passed to
     * {@link DRxLivePagedListBuilder#setBoundaryCallback}), the callbacks may be issued multiple
     * times. If for example {@link #onItemAtEndLoaded(Object)} triggers a network load, it should
     * avoid triggering it again while the load is ongoing.
     * <p>
     * The database + network Repository in the
     * <a href="https://github.com/googlesamples/android-architecture-components/blob/master/PagingWithNetworkSample/README.md">PagingWithNetworkSample</a>
     * shows how to implement a network BoundaryCallback using
     * <a href="https://square.github.io/retrofit/">Retrofit</a>, while
     * handling swipe-to-refresh, network errors, and retry.
     * <h4>Requesting Network Data</h4>
     * BoundaryCallback only passes the item at front or end of the list when out of data. This
     * makes it an easy fit for item-keyed network requests, where you can use the item passed to
     * the BoundaryCallback to request more data from the network. In these cases, the source of
     * truth for next page to load is coming from local storage, based on what's already loaded.
     * <p>
     * If you aren't using an item-keyed network API, you may be using page-keyed, or page-indexed.
     * If this is the case, the paging library doesn't know about the page key or index used in the
     * BoundaryCallback, so you need to track it yourself. You can do this in one of two ways:
     * <h5>Local storage Page key</h5>
     * If you want to perfectly resume your query, even if the app is killed and resumed, you can
     * store the key on disk. Note that with a positional/page index network API, there's a simple
     * way to do this, by using the {@code listSize} as an input to the next load (or
     * {@code listSize / NETWORK_PAGE_SIZE}, for page indexing).
     * <p>
     * The current list size isn't passed to the BoundaryCallback though. This is because the
     * DRxPagedList doesn't necessarily know the number of items in local storage. Placeholders may be
     * disabled, or the DRxDataSource may not count total number of items.
     * <p>
     * Instead, for these positional cases, you can query the database for the number of items, and
     * pass that to the network.
     * <h5>In-Memory Page key</h5>
     * Often it doesn't make sense to query the next page from network if the last page you fetched
     * was loaded many hours or days before. If you keep the key in memory, you can refresh any time
     * you start paging from a network source.
     * <p>
     * Store the next key in memory, inside your BoundaryCallback. When you create a new
     * BoundaryCallback when creating a new {@code LiveData}/{@code Observable} of
     * {@code DRxPagedList}, refresh data. For example,
     * <a href="https://codelabs.developers.google.com/codelabs/android-paging/index.html#8">in the
     * Paging Codelab</a>, the GitHub network page index is stored in memory.
     *
     * @param <T> Type loaded by the DRxPagedList.
     */
    @MainThread
    public abstract static class BoundaryCallback<T> {
        /**
         * Called when zero items are returned from an initial load of the DRxPagedList's data source.
         */
        public void onZeroItemsLoaded() {
        }

        /**
         * Called when the item at the front of the DRxPagedList has been loaded, and access has
         * occurred within {@link DRxPagedList.Config#prefetchDistance} of it.
         * <p>
         * No more data will be prepended to the DRxPagedList before this item.
         *
         * @param itemAtFront The first item of DRxPagedList
         */
        public void onItemAtFrontLoaded(@NonNull T itemAtFront) {
        }

        /**
         * Called when the item at the end of the DRxPagedList has been loaded, and access has
         * occurred within {@link DRxPagedList.Config#prefetchDistance} of it.
         * <p>
         * No more data will be appended to the DRxPagedList after this item.
         *
         * @param itemAtEnd The first item of DRxPagedList
         */
        public void onItemAtEndLoaded(@NonNull T itemAtEnd) {
        }
    }
}

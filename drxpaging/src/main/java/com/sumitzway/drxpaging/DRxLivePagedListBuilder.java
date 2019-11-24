package com.sumitzway.drxpaging;


import android.annotation.SuppressLint;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.lifecycle.ComputableLiveData;
import androidx.lifecycle.LiveData;

import java.util.concurrent.Executor;


public final class DRxLivePagedListBuilder<Key, Value> {
    private Key mInitialLoadKey;
    private DRxPagedList.Config mConfig;
    private DRxDataSource.Factory<Key, Value> mDataSourceFactory;
    private DRxPagedList.BoundaryCallback mBoundaryCallback;
    @SuppressLint("RestrictedApi")
    private Executor mFetchExecutor = ArchTaskExecutor.getIOThreadExecutor();

    /**
     * Creates a DRxLivePagedListBuilder with required parameters.
     *
     * @param dataSourceFactory DRxDataSource factory providing DRxDataSource generations.
     * @param config Paging configuration.
     */
    public DRxLivePagedListBuilder(@NonNull DRxDataSource.Factory<Key, Value> dataSourceFactory,
                                   @NonNull DRxPagedList.Config config) {
        //noinspection ConstantConditions
        if (config == null) {
            throw new IllegalArgumentException("DRxPagedList.Config must be provided");
        }
        //noinspection ConstantConditions
        if (dataSourceFactory == null) {
            throw new IllegalArgumentException("DRxDataSource.Factory must be provided");
        }

        mDataSourceFactory = dataSourceFactory;
        mConfig = config;
    }

    /**
     * Creates a DRxLivePagedListBuilder with required parameters.
     * <p>
     * This method is a convenience for:
     * <pre>
     * DRxLivePagedListBuilder(dataSourceFactory,
     *         new DRxPagedList.Config.Builder().setPageSize(pageSize).build())
     * </pre>
     *
     * @param dataSourceFactory DRxDataSource.Factory providing DRxDataSource generations.
     * @param pageSize Size of pages to load.
     */
    public DRxLivePagedListBuilder(@NonNull DRxDataSource.Factory<Key, Value> dataSourceFactory,
                                   int pageSize) {
        this(dataSourceFactory, new DRxPagedList.Config.Builder().setPageSize(pageSize).build());
    }

    /**
     * First loading key passed to the first DRxPagedList/DRxDataSource.
     * <p>
     * When a new DRxPagedList/DRxDataSource pair is created after the first, it acquires a load key from
     * the previous generation so that data is loaded around the position already being observed.
     *
     * @param key Initial load key passed to the first DRxPagedList/DRxDataSource.
     * @return this
     */
    @NonNull
    public DRxLivePagedListBuilder<Key, Value> setInitialLoadKey(@Nullable Key key) {
        mInitialLoadKey = key;
        return this;
    }

    /**
     * Sets a {@link DRxPagedList.BoundaryCallback} on each DRxPagedList created, typically used to load
     * additional data from network when paging from local storage.
     * <p>
     * Pass a BoundaryCallback to listen to when the DRxPagedList runs out of data to load. If this
     * method is not called, or {@code null} is passed, you will not be notified when each
     * DRxDataSource runs out of data to provide to its DRxPagedList.
     * <p>
     * If you are paging from a DRxDataSource.Factory backed by local storage, you can set a
     * BoundaryCallback to know when there is no more information to page from local storage.
     * This is useful to page from the network when local storage is a cache of network data.
     * <p>
     * Note that when using a BoundaryCallback with a {@code LiveData<DRxPagedList>}, method calls
     * on the callback may be dispatched multiple times - one for each DRxPagedList/DRxDataSource
     * pair. If loading network data from a BoundaryCallback, you should prevent multiple
     * dispatches of the same method from triggering multiple simultaneous network loads.
     *
     * @param boundaryCallback The boundary callback for listening to DRxPagedList load state.
     * @return this
     */
    @SuppressWarnings("unused")
    @NonNull
    public DRxLivePagedListBuilder<Key, Value> setBoundaryCallback(
            @Nullable DRxPagedList.BoundaryCallback<Value> boundaryCallback) {
        mBoundaryCallback = boundaryCallback;
        return this;
    }

    /**
     * Sets executor used for background fetching of PagedLists, and the pages within.
     * <p>
     * If not set, defaults to the Arch components I/O thread pool.
     *
     * @param fetchExecutor Executor for fetching data from DataSources.
     * @return this
     */
    @SuppressWarnings("unused")
    @NonNull
    public DRxLivePagedListBuilder<Key, Value> setFetchExecutor(
            @NonNull Executor fetchExecutor) {
        mFetchExecutor = fetchExecutor;
        return this;
    }

    /**
     * Constructs the {@code LiveData<DRxPagedList>}.
     * <p>
     * No work (such as loading) is done immediately, the creation of the first DRxPagedList is is
     * deferred until the LiveData is observed.
     *
     * @return The LiveData of PagedLists
     */
    @NonNull
    @SuppressLint("RestrictedApi")
    public LiveData<DRxPagedList<Value>> build() {
        return create(mInitialLoadKey, mConfig, mBoundaryCallback, mDataSourceFactory,
                ArchTaskExecutor.getMainThreadExecutor(), mFetchExecutor);
    }

    @AnyThread
    @NonNull
    @SuppressLint("RestrictedApi")
    private static <Key, Value> LiveData<DRxPagedList<Value>> create(
            @Nullable final Key initialLoadKey,
            @NonNull final DRxPagedList.Config config,
            @Nullable final DRxPagedList.BoundaryCallback boundaryCallback,
            @NonNull final DRxDataSource.Factory<Key, Value> dataSourceFactory,
            @NonNull final Executor notifyExecutor,
            @NonNull final Executor fetchExecutor) {
        return new ComputableLiveData<DRxPagedList<Value>>(fetchExecutor) {
            @Nullable
            private DRxPagedList<Value> mList;
            @Nullable
            private DRxDataSource<Key, Value> mDataSource;

            private final DRxDataSource.InvalidatedCallback mCallback =
                    new DRxDataSource.InvalidatedCallback() {
                        @Override
                        public void onInvalidated() {
                            invalidate();
                        }
                    };

            @SuppressWarnings("unchecked") // for casting getLastKey to Key
            @Override
            protected DRxPagedList<Value> compute() {
                @Nullable Key initializeKey = initialLoadKey;
                if (mList != null) {
                    initializeKey = (Key) mList.getLastKey();
                }

                do {
                    if (mDataSource != null) {
                        mDataSource.removeInvalidatedCallback(mCallback);
                    }

                    mDataSource = dataSourceFactory.create();
                    mDataSource.addInvalidatedCallback(mCallback);

                    mList = new DRxPagedList.Builder<>(mDataSource, config)
                            .setNotifyExecutor(notifyExecutor)
                            .setFetchExecutor(fetchExecutor)
                            .setBoundaryCallback(boundaryCallback)
                            .setInitialKey(initializeKey)
                            .build();
                } while (mList.isDetached());
                return mList;
            }
        }.getLiveData();
    }
}


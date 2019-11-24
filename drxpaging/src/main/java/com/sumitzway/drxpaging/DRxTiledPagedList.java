package com.sumitzway.drxpaging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.concurrent.Executor;

class DRxTiledPagedList<T> extends DRxPagedList<T>
        implements DRxPagedStorage.Callback {
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final DRxPositionalDataSource<T> mDataSource;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
            DRxPageResult.Receiver<T> mReceiver = new DRxPageResult.Receiver<T>() {
        // Creation thread for initial synchronous load, otherwise main thread
        // Safe to access main thread only state - no other thread has reference during construction
        @AnyThread
        @Override
        public void onPageResult(@DRxPageResult.ResultType int type,
                                 @NonNull DRxPageResult<T> pageResult) {
            if (pageResult.isInvalid()) {
                detach();
                return;
            }

            if (isDetached()) {
                // No op, have detached
                return;
            }

            if (type != DRxPageResult.INIT && type != DRxPageResult.TILE) {
                throw new IllegalArgumentException("unexpected resultType" + type);
            }

            List<T> page = pageResult.page;
            if (mStorage.getPageCount() == 0) {
                mStorage.initAndSplit(
                        pageResult.leadingNulls, page, pageResult.trailingNulls,
                        pageResult.positionOffset, mConfig.pageSize, DRxTiledPagedList.this);
            } else {
                mStorage.tryInsertPageAndTrim(
                        pageResult.positionOffset,
                        page,
                        mLastLoad,
                        mConfig.maxSize,
                        mRequiredRemainder,
                        DRxTiledPagedList.this);
            }

            if (mBoundaryCallback != null) {
                boolean deferEmpty = mStorage.size() == 0;
                boolean deferBegin = !deferEmpty
                        && pageResult.leadingNulls == 0
                        && pageResult.positionOffset == 0;
                int size = size();
                boolean deferEnd = !deferEmpty
                        && ((type == DRxPageResult.INIT && pageResult.trailingNulls == 0)
                        || (type == DRxPageResult.TILE
                        && (pageResult.positionOffset + mConfig.pageSize >= size)));
                deferBoundaryCallbacks(deferEmpty, deferBegin, deferEnd);
            }
        }
    };

    @WorkerThread
    DRxTiledPagedList(@NonNull DRxPositionalDataSource<T> dataSource,
                      @NonNull Executor mainThreadExecutor,
                      @NonNull Executor backgroundThreadExecutor,
                      @Nullable BoundaryCallback<T> boundaryCallback,
                      @NonNull Config config,
                      int position) {
        super(new DRxPagedStorage<T>(), mainThreadExecutor, backgroundThreadExecutor,
                boundaryCallback, config);
        mDataSource = dataSource;

        final int pageSize = mConfig.pageSize;
        mLastLoad = position;

        if (mDataSource.isInvalid()) {
            detach();
        } else {
            final int firstLoadSize =
                    (Math.max(mConfig.initialLoadSizeHint / pageSize, 2)) * pageSize;

            final int idealStart = position - firstLoadSize / 2;
            final int roundedPageStart = Math.max(0, idealStart / pageSize * pageSize);

            mDataSource.dispatchLoadInitial(true, roundedPageStart, firstLoadSize,
                    pageSize, mMainThreadExecutor, mReceiver);
        }
    }

    @Override
    boolean isContiguous() {
        return false;
    }

    @NonNull
    @Override
    public DRxDataSource<?, T> getDataSource() {
        return mDataSource;
    }

    @Nullable
    @Override
    public Object getLastKey() {
        return mLastLoad;
    }

    @Override
    protected void dispatchUpdatesSinceSnapshot(@NonNull DRxPagedList<T> DRxPagedListSnapshot,
                                                @NonNull Callback callback) {
        //noinspection UnnecessaryLocalVariable
        final DRxPagedStorage<T> snapshot = DRxPagedListSnapshot.mStorage;

        if (snapshot.isEmpty()
                || mStorage.size() != snapshot.size()) {
            throw new IllegalArgumentException("Invalid snapshot provided - doesn't appear"
                    + " to be a snapshot of this DRxPagedList");
        }

        // loop through each page and signal the callback for any pages that are present now,
        // but not in the snapshot.
        final int pageSize = mConfig.pageSize;
        final int leadingNullPages = mStorage.getLeadingNullCount() / pageSize;
        final int pageCount = mStorage.getPageCount();
        for (int i = 0; i < pageCount; i++) {
            int pageIndex = i + leadingNullPages;
            int updatedPages = 0;
            // count number of consecutive pages that were added since the snapshot...
            while (updatedPages < mStorage.getPageCount()
                    && mStorage.hasPage(pageSize, pageIndex + updatedPages)
                    && !snapshot.hasPage(pageSize, pageIndex + updatedPages)) {
                updatedPages++;
            }
            // and signal them all at once to the callback
            if (updatedPages > 0) {
                callback.onChanged(pageIndex * pageSize, pageSize * updatedPages);
                i += updatedPages - 1;
            }
        }
    }

    @Override
    protected void loadAroundInternal(int index) {
        mStorage.allocatePlaceholders(index, mConfig.prefetchDistance, mConfig.pageSize, this);
    }

    @Override
    public void onInitialized(int count) {
        notifyInserted(0, count);
    }

    @Override
    public void onPagePrepended(int leadingNulls, int changed, int added) {
        throw new IllegalStateException("Contiguous callback on DRxTiledPagedList");
    }

    @Override
    public void onPageAppended(int endPosition, int changed, int added) {
        throw new IllegalStateException("Contiguous callback on DRxTiledPagedList");
    }

    @Override
    public void onEmptyPrepend() {
        throw new IllegalStateException("Contiguous callback on DRxTiledPagedList");
    }

    @Override
    public void onEmptyAppend() {
        throw new IllegalStateException("Contiguous callback on DRxTiledPagedList");
    }

    @Override
    public void onPagePlaceholderInserted(final int pageIndex) {
        // placeholder means initialize a load
        mBackgroundThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isDetached()) {
                    return;
                }
                final int pageSize = mConfig.pageSize;

                if (mDataSource.isInvalid()) {
                    detach();
                } else {
                    int startPosition = pageIndex * pageSize;
                    int count = Math.min(pageSize, mStorage.size() - startPosition);
                    mDataSource.dispatchLoadRange(
                            DRxPageResult.TILE, startPosition, count, mMainThreadExecutor, mReceiver);
                }
            }
        });
    }

    @Override
    public void onPageInserted(int start, int count) {
        notifyChanged(start, count);
    }

    @Override
    public void onPagesRemoved(int startOfDrops, int count) {
        notifyRemoved(startOfDrops, count);
    }

    @Override
    public void onPagesSwappedToPlaceholder(int startOfDrops, int count) {
        notifyChanged(startOfDrops, count);
    }
}

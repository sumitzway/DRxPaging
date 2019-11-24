package com.sumitzway.drxpaging;



import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


class DRxSnapshotPagedList<T> extends DRxPagedList<T> {
    private final boolean mContiguous;
    private final Object mLastKey;
    private final DRxDataSource<?, T> mDataSource;

    DRxSnapshotPagedList(@NonNull DRxPagedList<T> DRxPagedList) {
        super(DRxPagedList.mStorage.snapshot(),
                DRxPagedList.mMainThreadExecutor,
                DRxPagedList.mBackgroundThreadExecutor,
                null,
                DRxPagedList.mConfig);
        mDataSource = DRxPagedList.getDataSource();
        mContiguous = DRxPagedList.isContiguous();
        mLastLoad = DRxPagedList.mLastLoad;
        mLastKey = DRxPagedList.getLastKey();
    }

    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public boolean isDetached() {
        return true;
    }

    @Override
    boolean isContiguous() {
        return mContiguous;
    }

    @Nullable
    @Override
    public Object getLastKey() {
        return mLastKey;
    }

    @NonNull
    @Override
    public DRxDataSource<?, T> getDataSource() {
        return mDataSource;
    }

    @Override
    void dispatchUpdatesSinceSnapshot(@NonNull DRxPagedList<T> storageSnapshot,
                                      @NonNull Callback callback) {
    }

    @Override
    void loadAroundInternal(int index) {
    }
}

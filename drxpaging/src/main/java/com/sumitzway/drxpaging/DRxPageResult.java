package com.sumitzway.drxpaging;


import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.util.Collections;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.SOURCE;

class DRxPageResult<T> {
    /**
     * Single empty instance to avoid allocations.
     * <p>
     * Note, distinct from {@link #INVALID_RESULT} because {@link #isInvalid()} checks instance.
     */
    @SuppressWarnings("unchecked")
    private static final DRxPageResult EMPTY_RESULT =
            new DRxPageResult(Collections.emptyList(), 0);

    @SuppressWarnings("unchecked")
    private static final DRxPageResult INVALID_RESULT =
            new DRxPageResult(Collections.emptyList(), 0);

    @SuppressWarnings("unchecked")
    static <T> DRxPageResult<T> getEmptyResult() {
        return EMPTY_RESULT;
    }

    @SuppressWarnings("unchecked")
    static <T> DRxPageResult<T> getInvalidResult() {
        return INVALID_RESULT;
    }

    @Retention(SOURCE)
    @IntDef({INIT, APPEND, PREPEND, TILE})
    @interface ResultType {
    }

    static final int INIT = 0;

    // contiguous results
    static final int APPEND = 1;
    static final int PREPEND = 2;

    // non-contiguous, tile result
    static final int TILE = 3;

    @NonNull
    public final List<T> page;
    @SuppressWarnings("WeakerAccess")
    public final int leadingNulls;
    @SuppressWarnings("WeakerAccess")
    public final int trailingNulls;
    @SuppressWarnings("WeakerAccess")
    public final int positionOffset;

    DRxPageResult(@NonNull List<T> list, int leadingNulls, int trailingNulls, int positionOffset) {
        this.page = list;
        this.leadingNulls = leadingNulls;
        this.trailingNulls = trailingNulls;
        this.positionOffset = positionOffset;
    }

    DRxPageResult(@NonNull List<T> list, int positionOffset) {
        this.page = list;
        this.leadingNulls = 0;
        this.trailingNulls = 0;
        this.positionOffset = positionOffset;
    }

    @Override
    public String toString() {
        return "Result " + leadingNulls
                + ", " + page
                + ", " + trailingNulls
                + ", offset " + positionOffset;
    }

    public boolean isInvalid() {
        return this == INVALID_RESULT;
    }

    abstract static class Receiver<T> {
        @MainThread
        public abstract void onPageResult(@DRxPageResult.ResultType int type, @NonNull DRxPageResult<T> pageResult);
    }
}

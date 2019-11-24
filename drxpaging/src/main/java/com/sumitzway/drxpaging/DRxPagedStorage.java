package com.sumitzway.drxpaging;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Class holding the pages of data backing a DRxPagedList, presenting sparse loaded data as a List.
 * <p>
 * It has two modes of operation: contiguous and non-contiguous (tiled). This class only holds
 * data, and does not have any notion of the ideas of async loads, or prefetching.
 */
final class DRxPagedStorage<T> extends AbstractList<T> {
    /**
     * Lists instances are compared (with instance equality) to PLACEHOLDER_LIST to check if an item
     * in that position is already loading. We use a singleton placeholder list that is distinct
     * from Collections.emptyList() for safety.
     */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final List PLACEHOLDER_LIST = new ArrayList();

    // Always set
    private int mLeadingNullCount;
    /**
     * List of pages in storage.
     *
     * Two storage modes:
     *
     * Contiguous - all content in mPages is valid and loaded, but may return false from isTiled().
     *     Safe to access any item in any page.
     *
     * Non-contiguous - mPages may have nulls or a placeholder page, isTiled() always returns true.
     *     mPages may have nulls, or placeholder (empty) pages while content is loading.
     */
    private final ArrayList<List<T>> mPages;
    private int mTrailingNullCount;

    private int mPositionOffset;
    /**
     * Number of loaded items held by {@link #mPages}. When tiling, doesn't count unloaded pages in
     * {@link #mPages}. If tiling is disabled, same as {@link #mStorageCount}.
     *
     * This count is the one used for trimming.
     */
    private int mLoadedCount;

    /**
     * Number of items represented by {@link #mPages}. If tiling is enabled, unloaded items in
     * {@link #mPages} may be null, but this value still counts them.
     */
    private int mStorageCount;

    // If mPageSize > 0, tiling is enabled, 'mPages' may have gaps, and leadingPages is set
    private int mPageSize;

    private int mNumberPrepended;
    private int mNumberAppended;

    DRxPagedStorage() {
        mLeadingNullCount = 0;
        mPages = new ArrayList<>();
        mTrailingNullCount = 0;
        mPositionOffset = 0;
        mLoadedCount = 0;
        mStorageCount = 0;
        mPageSize = 1;
        mNumberPrepended = 0;
        mNumberAppended = 0;
    }

    DRxPagedStorage(int leadingNulls, List<T> page, int trailingNulls) {
        this();
        init(leadingNulls, page, trailingNulls, 0);
    }

    private DRxPagedStorage(DRxPagedStorage<T> other) {
        mLeadingNullCount = other.mLeadingNullCount;
        mPages = new ArrayList<>(other.mPages);
        mTrailingNullCount = other.mTrailingNullCount;
        mPositionOffset = other.mPositionOffset;
        mLoadedCount = other.mLoadedCount;
        mStorageCount = other.mStorageCount;
        mPageSize = other.mPageSize;
        mNumberPrepended = other.mNumberPrepended;
        mNumberAppended = other.mNumberAppended;
    }

    DRxPagedStorage<T> snapshot() {
        return new DRxPagedStorage<>(this);
    }

    private void init(int leadingNulls, List<T> page, int trailingNulls, int positionOffset) {
        mLeadingNullCount = leadingNulls;
        mPages.clear();
        mPages.add(page);
        mTrailingNullCount = trailingNulls;

        mPositionOffset = positionOffset;
        mLoadedCount = page.size();
        mStorageCount = mLoadedCount;

        // initialized as tiled. There may be 3 nulls, 2 items, but we still call this tiled
        // even if it will break if nulls convert.
        mPageSize = page.size();

        mNumberPrepended = 0;
        mNumberAppended = 0;
    }

    void init(int leadingNulls, @NonNull List<T> page, int trailingNulls, int positionOffset,
              @NonNull DRxPagedStorage.Callback callback) {
        init(leadingNulls, page, trailingNulls, positionOffset);
        callback.onInitialized(size());
    }

    @Override
    public T get(int i) {
        if (i < 0 || i >= size()) {
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + size());
        }

        // is it definitely outside 'mPages'?
        int localIndex = i - mLeadingNullCount;
        if (localIndex < 0 || localIndex >= mStorageCount) {
            return null;
        }

        int localPageIndex;
        int pageInternalIndex;

        if (isTiled()) {
            // it's inside mPages, and we're tiled. Jump to correct tile.
            localPageIndex = localIndex / mPageSize;
            pageInternalIndex = localIndex % mPageSize;
        } else {
            // it's inside mPages, but page sizes aren't regular. Walk to correct tile.
            // Pages can only be null while tiled, so accessing page count is safe.
            pageInternalIndex = localIndex;
            final int localPageCount = mPages.size();
            for (localPageIndex = 0; localPageIndex < localPageCount; localPageIndex++) {
                int pageSize = mPages.get(localPageIndex).size();
                if (pageSize > pageInternalIndex) {
                    // stop, found the page
                    break;
                }
                pageInternalIndex -= pageSize;
            }
        }

        List<T> page = mPages.get(localPageIndex);
        if (page == null || page.size() == 0) {
            // can only occur in tiled case, with untouched inner/placeholder pages
            return null;
        }
        return page.get(pageInternalIndex);
    }

    /**
     * Returns true if all pages are the same size, except for the last, which may be smaller
     */
    boolean isTiled() {
        return mPageSize > 0;
    }

    int getLeadingNullCount() {
        return mLeadingNullCount;
    }

    int getTrailingNullCount() {
        return mTrailingNullCount;
    }

    int getStorageCount() {
        return mStorageCount;
    }

    int getNumberAppended() {
        return mNumberAppended;
    }

    int getNumberPrepended() {
        return mNumberPrepended;
    }

    int getPageCount() {
        return mPages.size();
    }

    int getLoadedCount() {
        return mLoadedCount;
    }

    interface Callback {
        void onInitialized(int count);
        void onPagePrepended(int leadingNulls, int changed, int added);
        void onPageAppended(int endPosition, int changed, int added);
        void onPagePlaceholderInserted(int pageIndex);
        void onPageInserted(int start, int count);
        void onPagesRemoved(int startOfDrops, int count);
        void onPagesSwappedToPlaceholder(int startOfDrops, int count);
        void onEmptyPrepend();
        void onEmptyAppend();
    }

    int getPositionOffset() {
        return mPositionOffset;
    }

    int getMiddleOfLoadedRange() {
        return mLeadingNullCount + mPositionOffset + mStorageCount / 2;
    }

    @Override
    public int size() {
        return mLeadingNullCount + mStorageCount + mTrailingNullCount;
    }

    int computeLeadingNulls() {
        int total = mLeadingNullCount;
        final int pageCount = mPages.size();
        for (int i = 0; i < pageCount; i++) {
            List page = mPages.get(i);
            if (page != null && page != PLACEHOLDER_LIST) {
                break;
            }
            total += mPageSize;
        }
        return total;
    }

    int computeTrailingNulls() {
        int total = mTrailingNullCount;
        for (int i = mPages.size() - 1; i >= 0; i--) {
            List page = mPages.get(i);
            if (page != null && page != PLACEHOLDER_LIST) {
                break;
            }
            total += mPageSize;
        }
        return total;
    }

    // ---------------- Trimming API -------------------
    // Trimming is always done at the beginning or end of the list, as content is loaded.
    // In addition to trimming pages in the storage, we also support pre-trimming pages (dropping
    // them just before they're added) to avoid dispatching an add followed immediately by a trim.
    //
    // Note - we avoid trimming down to a single page to reduce chances of dropping page in
    // viewport, since we don't strictly know the viewport. If trim is aggressively set to size of a
    // single page, trimming while the user can see a page boundary is dangerous. To be safe, we
    // just avoid trimming in these cases entirely.

    private boolean needsTrim(int maxSize, int requiredRemaining, int localPageIndex) {
        List<T> page = mPages.get(localPageIndex);
        return page == null || (mLoadedCount > maxSize
                && mPages.size() > 2
                && page != PLACEHOLDER_LIST
                && mLoadedCount - page.size() >= requiredRemaining);
    }

    boolean needsTrimFromFront(int maxSize, int requiredRemaining) {
        return needsTrim(maxSize, requiredRemaining, 0);
    }

    boolean needsTrimFromEnd(int maxSize, int requiredRemaining) {
        return needsTrim(maxSize, requiredRemaining, mPages.size() - 1);
    }

    boolean shouldPreTrimNewPage(int maxSize, int requiredRemaining, int countToBeAdded) {
        return mLoadedCount + countToBeAdded > maxSize
                && mPages.size() > 1
                && mLoadedCount >= requiredRemaining;
    }

    boolean trimFromFront(boolean insertNulls, int maxSize, int requiredRemaining,
                          @NonNull DRxPagedStorage.Callback callback) {
        int totalRemoved = 0;
        while (needsTrimFromFront(maxSize, requiredRemaining)) {
            List page = mPages.remove(0);
            int removed = (page == null) ? mPageSize : page.size();
            totalRemoved += removed;
            mStorageCount -= removed;
            mLoadedCount -= (page == null) ? 0 : page.size();
        }

        if (totalRemoved > 0) {
            if (insertNulls) {
                // replace removed items with nulls
                int previousLeadingNulls = mLeadingNullCount;
                mLeadingNullCount += totalRemoved;
                callback.onPagesSwappedToPlaceholder(previousLeadingNulls, totalRemoved);
            } else {
                // simply remove, and handle offset
                mPositionOffset += totalRemoved;
                callback.onPagesRemoved(mLeadingNullCount, totalRemoved);
            }
        }
        return totalRemoved > 0;
    }

    boolean trimFromEnd(boolean insertNulls, int maxSize, int requiredRemaining,
                        @NonNull DRxPagedStorage.Callback callback) {
        int totalRemoved = 0;
        while (needsTrimFromEnd(maxSize, requiredRemaining)) {
            List page = mPages.remove(mPages.size() - 1);
            int removed = (page == null) ? mPageSize : page.size();
            totalRemoved += removed;
            mStorageCount -= removed;
            mLoadedCount -= (page == null) ? 0 : page.size();
        }

        if (totalRemoved > 0) {
            int newEndPosition = mLeadingNullCount + mStorageCount;
            if (insertNulls) {
                // replace removed items with nulls
                mTrailingNullCount += totalRemoved;
                callback.onPagesSwappedToPlaceholder(newEndPosition, totalRemoved);
            } else {
                // items were just removed, signal
                callback.onPagesRemoved(newEndPosition, totalRemoved);
            }
        }
        return totalRemoved > 0;
    }

    // ---------------- Contiguous API -------------------

    T getFirstLoadedItem() {
        // safe to access first page's first item here:
        // If contiguous, mPages can't be empty, can't hold null Pages, and items can't be empty
        return mPages.get(0).get(0);
    }

    T getLastLoadedItem() {
        // safe to access last page's last item here:
        // If contiguous, mPages can't be empty, can't hold null Pages, and items can't be empty
        List<T> page = mPages.get(mPages.size() - 1);
        return page.get(page.size() - 1);
    }

    void prependPage(@NonNull List<T> page, @NonNull DRxPagedStorage.Callback callback) {
        final int count = page.size();
        if (count == 0) {
            // Nothing returned from source, stop loading in this direction
            callback.onEmptyPrepend();
            return;
        }
        if (mPageSize > 0 && count != mPageSize) {
            if (mPages.size() == 1 && count > mPageSize) {
                // prepending to a single item - update current page size to that of 'inner' page
                mPageSize = count;
            } else {
                // no longer tiled
                mPageSize = -1;
            }
        }

        mPages.add(0, page);
        mLoadedCount += count;
        mStorageCount += count;

        final int changedCount = Math.min(mLeadingNullCount, count);
        final int addedCount = count - changedCount;

        if (changedCount != 0) {
            mLeadingNullCount -= changedCount;
        }
        mPositionOffset -= addedCount;
        mNumberPrepended += count;

        callback.onPagePrepended(mLeadingNullCount, changedCount, addedCount);
    }

    void appendPage(@NonNull List<T> page, @NonNull DRxPagedStorage.Callback callback) {
        final int count = page.size();
        if (count == 0) {
            // Nothing returned from source, stop loading in this direction
            callback.onEmptyAppend();
            return;
        }

        if (mPageSize > 0) {
            // if the previous page was smaller than mPageSize,
            // or if this page is larger than the previous, disable tiling
            if (mPages.get(mPages.size() - 1).size() != mPageSize
                    || count > mPageSize) {
                mPageSize = -1;
            }
        }

        mPages.add(page);
        mLoadedCount += count;
        mStorageCount += count;

        final int changedCount = Math.min(mTrailingNullCount, count);
        final int addedCount = count - changedCount;

        if (changedCount != 0) {
            mTrailingNullCount -= changedCount;
        }
        mNumberAppended += count;
        callback.onPageAppended(mLeadingNullCount + mStorageCount - count,
                changedCount, addedCount);
    }

    // ------------------ Non-Contiguous API (tiling required) ----------------------

    /**
     * Return true if the page at the passed position would be the first (if trimFromFront) or last
     * page that's currently loading.
     */
    boolean pageWouldBeBoundary(int positionOfPage, boolean trimFromFront) {
        if (mPageSize < 1 || mPages.size() < 2) {
            throw new IllegalStateException("Trimming attempt before sufficient load");
        }

        if (positionOfPage < mLeadingNullCount) {
            // position represent page in leading nulls
            return trimFromFront;
        }

        if (positionOfPage >= mLeadingNullCount + mStorageCount) {
            // position represent page in trailing nulls
            return !trimFromFront;
        }

        int localPageIndex = (positionOfPage - mLeadingNullCount) / mPageSize;

        // walk outside in, return false if we find non-placeholder page before localPageIndex
        if (trimFromFront) {
            for (int i = 0; i < localPageIndex; i++) {
                if (mPages.get(i) != null) {
                    return false;
                }
            }
        } else {
            for (int i = mPages.size() - 1; i > localPageIndex; i--) {
                if (mPages.get(i) != null) {
                    return false;
                }
            }
        }

        // didn't find another page, so this one would be a boundary
        return true;
    }

    void initAndSplit(int leadingNulls, @NonNull List<T> multiPageList,
                      int trailingNulls, int positionOffset, int pageSize, @NonNull DRxPagedStorage.Callback callback) {

        int pageCount = (multiPageList.size() + (pageSize - 1)) / pageSize;
        for (int i = 0; i < pageCount; i++) {
            int beginInclusive = i * pageSize;
            int endExclusive = Math.min(multiPageList.size(), (i + 1) * pageSize);

            List<T> sublist = multiPageList.subList(beginInclusive, endExclusive);

            if (i == 0) {
                // Trailing nulls for first page includes other pages in multiPageList
                int initialTrailingNulls = trailingNulls + multiPageList.size() - sublist.size();
                init(leadingNulls, sublist, initialTrailingNulls, positionOffset);
            } else {
                int insertPosition = leadingNulls + beginInclusive;
                insertPage(insertPosition, sublist, null);
            }
        }
        callback.onInitialized(size());
    }

    void tryInsertPageAndTrim(
            int position,
            @NonNull List<T> page,
            int lastLoad,
            int maxSize,
            int requiredRemaining,
            @NonNull DRxPagedStorage.Callback callback) {
        boolean trim = maxSize != DRxPagedList.Config.MAX_SIZE_UNBOUNDED;
        boolean trimFromFront = lastLoad > getMiddleOfLoadedRange();

        boolean pageInserted = !trim
                || !shouldPreTrimNewPage(maxSize, requiredRemaining, page.size())
                || !pageWouldBeBoundary(position, trimFromFront);

        if (pageInserted) {
            insertPage(position, page, callback);
        } else {
            // trim would have us drop the page we just loaded - swap it to null
            int localPageIndex = (position - mLeadingNullCount) / mPageSize;
            mPages.set(localPageIndex, null);

            // note: we also remove it, so we don't have to guess how large a 'null' page is later
            mStorageCount -= page.size();
            if (trimFromFront) {
                mPages.remove(0);
                mLeadingNullCount += page.size();
            } else {
                mPages.remove(mPages.size() - 1);
                mTrailingNullCount += page.size();
            }
        }

        if (trim) {
            if (trimFromFront) {
                trimFromFront(true, maxSize, requiredRemaining, callback);
            } else {
                trimFromEnd(true, maxSize, requiredRemaining, callback);
            }
        }
    }

    public void insertPage(int position, @NonNull List<T> page, @Nullable DRxPagedStorage.Callback callback) {
        final int newPageSize = page.size();
        if (newPageSize != mPageSize) {
            // differing page size is OK in 2 cases, when the page is being added:
            // 1) to the end (in which case, ignore new smaller size)
            // 2) only the last page has been added so far (in which case, adopt new bigger size)

            int size = size();
            boolean addingLastPage = position == (size - size % mPageSize)
                    && newPageSize < mPageSize;
            boolean onlyEndPagePresent = mTrailingNullCount == 0 && mPages.size() == 1
                    && newPageSize > mPageSize;

            // OK only if existing single page, and it's the last one
            if (!onlyEndPagePresent && !addingLastPage) {
                throw new IllegalArgumentException("page introduces incorrect tiling");
            }
            if (onlyEndPagePresent) {
                mPageSize = newPageSize;
            }
        }

        int pageIndex = position / mPageSize;

        allocatePageRange(pageIndex, pageIndex);

        int localPageIndex = pageIndex - mLeadingNullCount / mPageSize;

        List<T> oldPage = mPages.get(localPageIndex);
        if (oldPage != null && oldPage != PLACEHOLDER_LIST) {
            throw new IllegalArgumentException(
                    "Invalid position " + position + ": data already loaded");
        }
        mPages.set(localPageIndex, page);
        mLoadedCount += newPageSize;
        if (callback != null) {
            callback.onPageInserted(position, newPageSize);
        }
    }

    void allocatePageRange(final int minimumPage, final int maximumPage) {
        int leadingNullPages = mLeadingNullCount / mPageSize;

        if (minimumPage < leadingNullPages) {
            for (int i = 0; i < leadingNullPages - minimumPage; i++) {
                mPages.add(0, null);
            }
            int newStorageAllocated = (leadingNullPages - minimumPage) * mPageSize;
            mStorageCount += newStorageAllocated;
            mLeadingNullCount -= newStorageAllocated;

            leadingNullPages = minimumPage;
        }
        if (maximumPage >= leadingNullPages + mPages.size()) {
            int newStorageAllocated = Math.min(mTrailingNullCount,
                    (maximumPage + 1 - (leadingNullPages + mPages.size())) * mPageSize);
            for (int i = mPages.size(); i <= maximumPage - leadingNullPages; i++) {
                mPages.add(mPages.size(), null);
            }
            mStorageCount += newStorageAllocated;
            mTrailingNullCount -= newStorageAllocated;
        }
    }

    public void allocatePlaceholders(int index, int prefetchDistance,
                                     int pageSize, DRxPagedStorage.Callback callback) {
        if (pageSize != mPageSize) {
            if (pageSize < mPageSize) {
                throw new IllegalArgumentException("Page size cannot be reduced");
            }
            if (mPages.size() != 1 || mTrailingNullCount != 0) {
                // not in single, last page allocated case - can't change page size
                throw new IllegalArgumentException(
                        "Page size can change only if last page is only one present");
            }
            mPageSize = pageSize;
        }

        final int maxPageCount = (size() + mPageSize - 1) / mPageSize;
        int minimumPage = Math.max((index - prefetchDistance) / mPageSize, 0);
        int maximumPage = Math.min((index + prefetchDistance) / mPageSize, maxPageCount - 1);

        allocatePageRange(minimumPage, maximumPage);
        int leadingNullPages = mLeadingNullCount / mPageSize;
        for (int pageIndex = minimumPage; pageIndex <= maximumPage; pageIndex++) {
            int localPageIndex = pageIndex - leadingNullPages;
            if (mPages.get(localPageIndex) == null) {
                //noinspection unchecked
                mPages.set(localPageIndex, PLACEHOLDER_LIST);
                callback.onPagePlaceholderInserted(pageIndex);
            }
        }
    }

    public boolean hasPage(int pageSize, int index) {
        // NOTE: we pass pageSize here to avoid in case mPageSize
        // not fully initialized (when last page only one loaded)
        int leadingNullPages = mLeadingNullCount / pageSize;

        if (index < leadingNullPages || index >= leadingNullPages + mPages.size()) {
            return false;
        }

        List<T> page = mPages.get(index - leadingNullPages);

        return page != null && page != PLACEHOLDER_LIST;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("leading " + mLeadingNullCount
                + ", storage " + mStorageCount
                + ", trailing " + getTrailingNullCount());

        for (int i = 0; i < mPages.size(); i++) {
            ret.append(" ").append(mPages.get(i));
        }
        return ret.toString();
    }
}

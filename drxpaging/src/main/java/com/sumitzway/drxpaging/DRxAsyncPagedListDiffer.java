package com.sumitzway.drxpaging;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.AdapterListUpdateCallback;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Helper object for mapping a {@link DRxPagedList} into a
 * {@link androidx.recyclerview.widget.RecyclerView.Adapter RecyclerView.Adapter}.
 * <p>
 * For simplicity, the {@link DRxPagedListAdapter} wrapper class can often be used instead of the
 * differ directly. This diff class is exposed for complex cases, and where overriding an adapter
 * base class to support paging isn't convenient.
 * <p>
 * When consuming a {@link LiveData} of DRxPagedList, you can observe updates and dispatch them
 * directly to {@link #submitList(DRxPagedList)}. The DRxAsyncPagedListDiffer then can present this
 * updating data set simply for an adapter. It listens to DRxPagedList loading callbacks, and uses
 * DiffUtil on a background thread to compute updates as new PagedLists are received.
 * <p>
 * It provides a simple list-like API with {@link #getItem(int)} and {@link #getItemCount()} for an
 * adapter to acquire and present data objects.
 * <p>
 * A complete usage pattern with Room would look like this:
 * <pre>
 * {@literal @}Dao
 * interface UserDao {
 *     {@literal @}Query("SELECT * FROM user ORDER BY lastName ASC")
 *     public abstract DRxDataSource.Factory&lt;Integer, User> usersByLastName();
 * }
 *
 * class MyViewModel extends ViewModel {
 *     public final LiveData&lt;DRxPagedList&lt;User>> usersList;
 *     public MyViewModel(UserDao userDao) {
 *         usersList = new DRxLivePagedListBuilder&lt;>(
 *                 userDao.usersByLastName(), /* page size {@literal *}/ 20).build();
 *     }
 * }
 *
 * class MyActivity extends AppCompatActivity {
 *     {@literal @}Override
 *     public void onCreate(Bundle savedState) {
 *         super.onCreate(savedState);
 *         MyViewModel viewModel = ViewModelProviders.of(this).get(MyViewModel.class);
 *         RecyclerView recyclerView = findViewById(R.id.user_list);
 *         final UserAdapter adapter = new UserAdapter();
 *         viewModel.usersList.observe(this, pagedList -> adapter.submitList(pagedList));
 *         recyclerView.setAdapter(adapter);
 *     }
 * }
 *
 * class UserAdapter extends RecyclerView.Adapter&lt;UserViewHolder> {
 *     private final DRxAsyncPagedListDiffer&lt;User> mDiffer
 *             = new DRxAsyncPagedListDiffer(this, DIFF_CALLBACK);
 *     {@literal @}Override
 *     public int getItemCount() {
 *         return mDiffer.getItemCount();
 *     }
 *     public void submitList(DRxPagedList&lt;User> pagedList) {
 *         mDiffer.submitList(pagedList);
 *     }
 *     {@literal @}Override
 *     public void onBindViewHolder(UserViewHolder holder, int position) {
 *         User user = mDiffer.getItem(position);
 *         if (user != null) {
 *             holder.bindTo(user);
 *         } else {
 *             // Null defines a placeholder item - DRxAsyncPagedListDiffer will automatically
 *             // invalidate this row when the actual object is loaded from the database
 *             holder.clear();
 *         }
 *     }
 *     public static final DiffUtil.ItemCallback&lt;User> DIFF_CALLBACK =
 *             new DiffUtil.ItemCallback&lt;User>() {
 *          {@literal @}Override
 *          public boolean areItemsTheSame(
 *                  {@literal @}NonNull User oldUser, {@literal @}NonNull User newUser) {
 *              // User properties may have changed if reloaded from the DB, but ID is fixed
 *              return oldUser.getId() == newUser.getId();
 *          }
 *          {@literal @}Override
 *          public boolean areContentsTheSame(
 *                  {@literal @}NonNull User oldUser, {@literal @}NonNull User newUser) {
 *              // NOTE: if you use equals, your object must properly override Object#equals()
 *              // Incorrectly returning false here will result in too many animations.
 *              return oldUser.equals(newUser);
 *          }
 *      }
 * }</pre>
 *
 * @param <T> Type of the PagedLists this differ will receive.
 */
public class DRxAsyncPagedListDiffer<T> {
    // updateCallback notifications must only be notified *after* new data and item count are stored
    // this ensures Adapter#notifyItemRangeInserted etc are accessing the new data
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final ListUpdateCallback mUpdateCallback;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final AsyncDifferConfig<T> mConfig;

    @SuppressWarnings("RestrictedApi")
    Executor mMainThreadExecutor = ArchTaskExecutor.getMainThreadExecutor();

    /**
     * Listener for when the current DRxPagedList is updated.
     *
     * @param <T> Type of items in DRxPagedList
     */
    public interface PagedListListener<T> {
        /**
         * Called after the current DRxPagedList has been updated.
         *
         * @param previousList The previous list, may be null.
         * @param currentList  The new current list, may be null.
         */
        void onCurrentListChanged(
                @Nullable DRxPagedList<T> previousList, @Nullable DRxPagedList<T> currentList);
    }

    private final List<PagedListListener<T>> mListeners = new CopyOnWriteArrayList<>();

    private boolean mIsContiguous;

    private DRxPagedList<T> mDRxPagedList;
    private DRxPagedList<T> mSnapshot;

    // Max generation of currently scheduled runnable
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            int mMaxScheduledGeneration;

    /**
     * Convenience for {@code DRxAsyncPagedListDiffer(new AdapterListUpdateCallback(adapter),
     * new AsyncDifferConfig.Builder<T>(diffCallback).build();}
     *
     * @param adapter      Adapter that will receive update signals.
     * @param diffCallback The {@link DiffUtil.ItemCallback DiffUtil.ItemCallback} instance to
     *                     compare items in the list.
     */
    @SuppressWarnings("WeakerAccess")
    public DRxAsyncPagedListDiffer(@NonNull RecyclerView.Adapter adapter,
                                   @NonNull DiffUtil.ItemCallback<T> diffCallback) {
        mUpdateCallback = new AdapterListUpdateCallback(adapter);
        mConfig = new AsyncDifferConfig.Builder<>(diffCallback).build();
    }

    @SuppressWarnings("WeakerAccess")
    public DRxAsyncPagedListDiffer(@NonNull ListUpdateCallback listUpdateCallback,
                                   @NonNull AsyncDifferConfig<T> config) {
        mUpdateCallback = listUpdateCallback;
        mConfig = config;
    }

    private DRxPagedList.Callback mPagedListCallback = new DRxPagedList.Callback() {
        @Override
        public void onInserted(int position, int count) {
            mUpdateCallback.onInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            mUpdateCallback.onRemoved(position, count);
        }

        @Override
        public void onChanged(int position, int count) {
            // NOTE: pass a null payload to convey null -> item
            mUpdateCallback.onChanged(position, count, null);
        }
    };

    /**
     * Get the item from the current DRxPagedList at the specified index.
     * <p>
     * Note that this operates on both loaded items and null padding within the DRxPagedList.
     *
     * @param index Index of item to get, must be >= 0, and &lt; {@link #getItemCount()}.
     * @return The item, or null, if a null placeholder is at the specified position.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public T getItem(int index) {
        if (mDRxPagedList == null) {
            if (mSnapshot == null) {
                throw new IndexOutOfBoundsException(
                        "Item count is zero, getItem() call is invalid");
            } else {
                return mSnapshot.get(index);
            }
        }
        mDRxPagedList.loadAround(index);
        return mDRxPagedList.get(index);
    }



    /**
     * Get the number of items currently presented by this Differ. This value can be directly
     * returned to {@link RecyclerView.Adapter#getItemCount()}.
     *
     * @return Number of items being presented.
     */
    @SuppressWarnings("WeakerAccess")
    public int getItemCount() {
        if (mDRxPagedList != null) {
            return mDRxPagedList.size();
        }

        return mSnapshot == null ? 0 : mSnapshot.size();
    }

    /**
     * Pass a new DRxPagedList to the differ.
     * <p>
     * If a DRxPagedList is already present, a diff will be computed asynchronously on a background
     * thread. When the diff is computed, it will be applied (dispatched to the
     * {@link ListUpdateCallback}), and the new DRxPagedList will be swapped in as the
     * {@link #getCurrentList() current list}.
     *
     * @param DRxPagedList The new DRxPagedList.
     */
    public void submitList(@Nullable final DRxPagedList<T> DRxPagedList) {
        submitList(DRxPagedList, null);
    }

    /**
     * Pass a new DRxPagedList to the differ.
     * <p>
     * If a DRxPagedList is already present, a diff will be computed asynchronously on a background
     * thread. When the diff is computed, it will be applied (dispatched to the
     * {@link ListUpdateCallback}), and the new DRxPagedList will be swapped in as the
     * {@link #getCurrentList() current list}.
     * <p>
     * The commit callback can be used to know when the DRxPagedList is committed, but note that it
     * may not be executed. If DRxPagedList B is submitted immediately after DRxPagedList A, and is
     * committed directly, the callback associated with DRxPagedList A will not be run.
     *
     * @param DRxPagedList      The new DRxPagedList.
     * @param commitCallback Optional runnable that is executed when the DRxPagedList is committed, if
     *                       it is committed.
     */
    @SuppressWarnings("ReferenceEquality")
    public void submitList(@Nullable final DRxPagedList<T> DRxPagedList,
                           @Nullable final Runnable commitCallback) {
        if (DRxPagedList != null) {
            if (mDRxPagedList == null && mSnapshot == null) {
                mIsContiguous = DRxPagedList.isContiguous();
            } else {
                if (DRxPagedList.isContiguous() != mIsContiguous) {
                    throw new IllegalArgumentException("DRxAsyncPagedListDiffer cannot handle both"
                            + " contiguous and non-contiguous lists.");
                }
            }
        }

        // incrementing generation means any currently-running diffs are discarded when they finish
        final int runGeneration = ++mMaxScheduledGeneration;

        if (DRxPagedList == mDRxPagedList) {
            // nothing to do (Note - still had to inc generation, since may have ongoing work)
            if (commitCallback != null) {
                commitCallback.run();
            }
            return;
        }

        final DRxPagedList<T> previous = (mSnapshot != null) ? mSnapshot : mDRxPagedList;

        if (DRxPagedList == null) {
            int removedCount = getItemCount();
            if (mDRxPagedList != null) {
                mDRxPagedList.removeWeakCallback(mPagedListCallback);
                mDRxPagedList = null;
            } else if (mSnapshot != null) {
                mSnapshot = null;
            }
            // dispatch update callback after updating mDRxPagedList/mSnapshot
            mUpdateCallback.onRemoved(0, removedCount);
            onCurrentListChanged(previous, null, commitCallback);
            return;
        }

        if (mDRxPagedList == null && mSnapshot == null) {
            // fast simple first insert
            mDRxPagedList = DRxPagedList;
            DRxPagedList.addWeakCallback(null, mPagedListCallback);

            // dispatch update callback after updating mDRxPagedList/mSnapshot
            mUpdateCallback.onInserted(0, DRxPagedList.size());

            onCurrentListChanged(null, DRxPagedList, commitCallback);
            return;
        }

        if (mDRxPagedList != null) {
            // first update scheduled on this list, so capture mPages as a snapshot, removing
            // callbacks so we don't have resolve updates against a moving target
            mDRxPagedList.removeWeakCallback(mPagedListCallback);
            mSnapshot = (DRxPagedList<T>) mDRxPagedList.snapshot();
            mDRxPagedList = null;
        }

        if (mSnapshot == null || mDRxPagedList != null) {
            throw new IllegalStateException("must be in snapshot state to diff");
        }

        final DRxPagedList<T> oldSnapshot = mSnapshot;
        final DRxPagedList<T> newSnapshot = (DRxPagedList<T>) DRxPagedList.snapshot();
        mConfig.getBackgroundThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final DiffUtil.DiffResult result;
                result = DRxPagedStorageDiffHelper.computeDiff(
                        oldSnapshot.mStorage,
                        newSnapshot.mStorage,
                        mConfig.getDiffCallback());

                mMainThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (mMaxScheduledGeneration == runGeneration) {
                            latchPagedList(DRxPagedList, newSnapshot, result,
                                    oldSnapshot.mLastLoad, commitCallback);
                        }
                    }
                });
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void latchPagedList(
            @NonNull DRxPagedList<T> newList,
            @NonNull DRxPagedList<T> diffSnapshot,
            @NonNull DiffUtil.DiffResult diffResult,
            int lastAccessIndex,
            @Nullable Runnable commitCallback) {
        if (mSnapshot == null || mDRxPagedList != null) {
            throw new IllegalStateException("must be in snapshot state to apply diff");
        }

        DRxPagedList<T> previousSnapshot = mSnapshot;
        mDRxPagedList = newList;
        mSnapshot = null;

        // dispatch update callback after updating mDRxPagedList/mSnapshot
        DRxPagedStorageDiffHelper.dispatchDiff(mUpdateCallback,
                previousSnapshot.mStorage, newList.mStorage, diffResult);

        newList.addWeakCallback(diffSnapshot, mPagedListCallback);

        if (!mDRxPagedList.isEmpty()) {
            // Transform the last loadAround() index from the old list to the new list by passing it
            // through the DiffResult. This ensures the lastKey of a positional DRxPagedList is carried
            // to new list even if no in-viewport item changes (DRxAsyncPagedListDiffer#get not called)
            // Note: we don't take into account loads between new list snapshot and new list, but
            // this is only a problem in rare cases when placeholders are disabled, and a load
            // starts (for some reason) and finishes before diff completes.
            int newPosition = DRxPagedStorageDiffHelper.transformAnchorIndex(
                    diffResult, previousSnapshot.mStorage, diffSnapshot.mStorage, lastAccessIndex);

            // Trigger load in new list at this position, clamped to list bounds.
            // This is a load, not just an update of last load position, since the new list may be
            // incomplete. If new list is subset of old list, but doesn't fill the viewport, this
            // will likely trigger a load of new data.
            mDRxPagedList.loadAround(Math.max(0, Math.min(mDRxPagedList.size() - 1, newPosition)));
        }

        onCurrentListChanged(previousSnapshot, mDRxPagedList, commitCallback);
    }

    private void onCurrentListChanged(
            @Nullable DRxPagedList<T> previousList,
            @Nullable DRxPagedList<T> currentList,
            @Nullable Runnable commitCallback) {
        for (DRxAsyncPagedListDiffer.PagedListListener<T> listener : mListeners) {
            listener.onCurrentListChanged(previousList, currentList);
        }
        if (commitCallback != null) {
            commitCallback.run();
        }
    }

    /**
     * Add a PagedListListener to receive updates when the current DRxPagedList changes.
     *
     * @param listener Listener to receive updates.
     * @see #getCurrentList()
     * @see #removePagedListListener(DRxAsyncPagedListDiffer.PagedListListener)
     */
    public void addPagedListListener(@NonNull DRxAsyncPagedListDiffer.PagedListListener<T> listener) {
        mListeners.add(listener);
    }

    /**
     * Remove a previously registered PagedListListener.
     *
     * @param listener Previously registered listener.
     * @see #getCurrentList()
     * @see #addPagedListListener(DRxAsyncPagedListDiffer.PagedListListener)
     */
    public void removePagedListListener(@NonNull DRxAsyncPagedListDiffer.PagedListListener<T> listener) {
        mListeners.remove(listener);
    }

    /**
     * Returns the DRxPagedList currently being displayed by the differ.
     * <p>
     * This is not necessarily the most recent list passed to {@link #submitList(DRxPagedList)},
     * because a diff is computed asynchronously between the new list and the current list before
     * updating the currentList value. May be null if no DRxPagedList is being presented.
     *
     * @return The list currently being displayed, may be null.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public DRxPagedList<T> getCurrentList() {
        if (mSnapshot != null) {
            return mSnapshot;
        }
        return mDRxPagedList;
    }
}

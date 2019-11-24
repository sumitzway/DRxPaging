package com.sumitzway.drxpaging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AdapterListUpdateCallback;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

/**
 * {@link RecyclerView.Adapter RecyclerView.Adapter} base class for presenting paged data from
 * {@link DRxPagedList}s in a {@link RecyclerView}.
 * <p>
 * This class is a convenience wrapper around {@link DRxAsyncPagedListDiffer} that implements common
 * default behavior for item counting, and listening to DRxPagedList update callbacks.
 * <p>
 * While using a LiveData&lt;DRxPagedList> is an easy way to provide data to the adapter, it isn't
 * required - you can use {@link #submitList(DRxPagedList)} when new lists are available.
 * <p>
 * DRxPagedListAdapter listens to DRxPagedList loading callbacks as pages are loaded, and uses DiffUtil on
 * a background thread to compute fine grained updates as new PagedLists are received.
 * <p>
 * Handles both the internal paging of the list as more data is loaded, and updates in the form of
 * new PagedLists.
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
 *         UserAdapter&lt;User> adapter = new UserAdapter();
 *         viewModel.usersList.observe(this, pagedList -> adapter.submitList(pagedList));
 *         recyclerView.setAdapter(adapter);
 *     }
 * }
 *
 * class UserAdapter extends DRxPagedListAdapter&lt;User, UserViewHolder> {
 *     public UserAdapter() {
 *         super(DIFF_CALLBACK);
 *     }
 *     {@literal @}Override
 *     public void onBindViewHolder(UserViewHolder holder, int position) {
 *         User user = getItem(position);
 *         if (user != null) {
 *             holder.bindTo(user);
 *         } else {
 *             // Null defines a placeholder item - DRxPagedListAdapter will automatically invalidate
 *             // this row when the actual object is loaded from the database
 *             holder.clear();
 *         }
 *     }
 *     public static final DiffUtil.ItemCallback&lt;User> DIFF_CALLBACK =
 *             new DiffUtil.ItemCallback&lt;User>() {
 *         {@literal @}Override
 *         public boolean areItemsTheSame(
 *                 {@literal @}NonNull User oldUser, {@literal @}NonNull User newUser) {
 *             // User properties may have changed if reloaded from the DB, but ID is fixed
 *             return oldUser.getId() == newUser.getId();
 *         }
 *         {@literal @}Override
 *         public boolean areContentsTheSame(
 *                 {@literal @}NonNull User oldUser, {@literal @}NonNull User newUser) {
 *             // NOTE: if you use equals, your object must properly override Object#equals()
 *             // Incorrectly returning false here will result in too many animations.
 *             return oldUser.equals(newUser);
 *         }
 *     }
 * }</pre>
 * <p>
 * Advanced users that wish for more control over adapter behavior, or to provide a specific base
 * class should refer to {@link DRxAsyncPagedListDiffer}, which provides the mapping from paging
 * events to adapter-friendly callbacks.
 *
 * @param <T>  Type of the PagedLists this Adapter will receive.
 * @param <VH> A class that extends ViewHolder that will be used by the adapter.
 */
public abstract class DRxPagedListAdapter<T, VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<VH> {
    final DRxAsyncPagedListDiffer<T> mDiffer;
    private final DRxAsyncPagedListDiffer.PagedListListener<T> mListener =
            new DRxAsyncPagedListDiffer.PagedListListener<T>() {
                @Override
                public void onCurrentListChanged(
                        @Nullable DRxPagedList<T> previousList, @Nullable DRxPagedList<T> currentList) {
                    DRxPagedListAdapter.this.onCurrentListChanged(currentList);
                    DRxPagedListAdapter.this.onCurrentListChanged(previousList, currentList);
                }
            };

    /**
     * Creates a DRxPagedListAdapter with default threading and
     * {@link androidx.recyclerview.widget.ListUpdateCallback}.
     * <p>
     * Convenience for {@link #DRxPagedListAdapter(AsyncDifferConfig)}, which uses default threading
     * behavior.
     *
     * @param diffCallback The {@link DiffUtil.ItemCallback DiffUtil.ItemCallback} instance to
     *                     compare items in the list.
     */
    protected DRxPagedListAdapter(@NonNull DiffUtil.ItemCallback<T> diffCallback) {
        mDiffer = new DRxAsyncPagedListDiffer<>(this, diffCallback);
        mDiffer.addPagedListListener(mListener);
    }

    protected DRxPagedListAdapter(@NonNull AsyncDifferConfig<T> config) {
        mDiffer = new DRxAsyncPagedListDiffer<>(new AdapterListUpdateCallback(this), config);
        mDiffer.addPagedListListener(mListener);
    }

    /**
     * Set the new list to be displayed.
     * <p>
     * If a list is already being displayed, a diff will be computed on a background thread, which
     * will dispatch Adapter.notifyItem events on the main thread.
     *
     * @param DRxPagedList The new list to be displayed.
     */
    public void submitList(@Nullable DRxPagedList<T> DRxPagedList) {
        mDiffer.submitList(DRxPagedList);
    }

    /**
     * Set the new list to be displayed.
     * <p>
     * If a list is already being displayed, a diff will be computed on a background thread, which
     * will dispatch Adapter.notifyItem events on the main thread.
     * <p>
     * The commit callback can be used to know when the DRxPagedList is committed, but note that it
     * may not be executed. If DRxPagedList B is submitted immediately after DRxPagedList A, and is
     * committed directly, the callback associated with DRxPagedList A will not be run.
     *
     * @param DRxPagedList      The new list to be displayed.
     * @param commitCallback Optional runnable that is executed when the DRxPagedList is committed, if
     *                       it is committed.
     */
    public void submitList(@Nullable DRxPagedList<T> DRxPagedList,
                           @Nullable final Runnable commitCallback) {
        mDiffer.submitList(DRxPagedList, commitCallback);
    }

    @Nullable
    protected T getItem(int position) {
        return mDiffer.getItem(position);
    }


    @Override
    public int getItemCount() {
        return mDiffer.getItemCount();
    }

    /**
     * Returns the DRxPagedList currently being displayed by the Adapter.
     * <p>
     * This is not necessarily the most recent list passed to {@link #submitList(DRxPagedList)},
     * because a diff is computed asynchronously between the new list and the current list before
     * updating the currentList value. May be null if no DRxPagedList is being presented.
     *
     * @return The list currently being displayed.
     * @see #onCurrentListChanged(DRxPagedList, DRxPagedList)
     */
    @Nullable
    public DRxPagedList<T> getCurrentList() {
        return mDiffer.getCurrentList();
    }

    /**
     * Called when the current DRxPagedList is updated.
     * <p>
     * This may be dispatched as part of {@link #submitList(DRxPagedList)} if a background diff isn't
     * needed (such as when the first list is passed, or the list is cleared). In either case,
     * DRxPagedListAdapter will simply call
     * {@link #notifyItemRangeInserted(int, int) notifyItemRangeInserted/Removed(0, mPreviousSize)}.
     * <p>
     * This method will <em>not</em>be called when the Adapter switches from presenting a DRxPagedList
     * to a snapshot version of the DRxPagedList during a diff. This means you cannot observe each
     * DRxPagedList via this method.
     *
     * @param currentList new DRxPagedList being displayed, may be null.
     * @see #getCurrentList()
     * @deprecated Use the two argument variant instead:
     * {@link #onCurrentListChanged(DRxPagedList, DRxPagedList)}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public void onCurrentListChanged(@Nullable DRxPagedList<T> currentList) {
    }

    /**
     * Called when the current DRxPagedList is updated.
     * <p>
     * This may be dispatched as part of {@link #submitList(DRxPagedList)} if a background diff isn't
     * needed (such as when the first list is passed, or the list is cleared). In either case,
     * DRxPagedListAdapter will simply call
     * {@link #notifyItemRangeInserted(int, int) notifyItemRangeInserted/Removed(0, mPreviousSize)}.
     * <p>
     * This method will <em>not</em>be called when the Adapter switches from presenting a DRxPagedList
     * to a snapshot version of the DRxPagedList during a diff. This means you cannot observe each
     * DRxPagedList via this method.
     *
     * @param previousList DRxPagedList that was previously displayed, may be null.
     * @param currentList  new DRxPagedList being displayed, may be null.
     * @see #getCurrentList()
     */
    public void onCurrentListChanged(
            @Nullable DRxPagedList<T> previousList, @Nullable DRxPagedList<T> currentList) {
    }
}

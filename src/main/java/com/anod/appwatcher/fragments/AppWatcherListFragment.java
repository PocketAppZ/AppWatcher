package com.anod.appwatcher.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.anod.appwatcher.AppWatcherActivity;
import com.anod.appwatcher.BuildConfig;
import com.anod.appwatcher.ChangelogActivity;
import com.anod.appwatcher.R;
import com.anod.appwatcher.adapters.AppViewHolder;
import com.anod.appwatcher.adapters.ListCursorAdapterWrapper;
import com.anod.appwatcher.model.AppInfo;
import com.anod.appwatcher.model.AppListCursorLoader;
import com.anod.appwatcher.model.Filters;
import com.anod.appwatcher.model.InstalledFilter;
import com.anod.appwatcher.utils.IntentUtils;
import com.anod.appwatcher.utils.PackageManagerUtils;

import info.anodsplace.android.log.AppLog;
import info.anodsplace.android.widget.recyclerview.MergeRecyclerAdapter;

public class AppWatcherListFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        AppWatcherActivity.QueryChangeListener,
        SwipeRefreshLayout.OnRefreshListener, AppViewHolder.OnClickListener {

    private static final int ADAPTER_WATCHLIST = 0;
    public static final String ARG_FILTER = "filter";
    private String mTitleFilter = "";


    public RecyclerView mListView;
    private View mProgressContainer;
    private View mEmptyView;

    private InstalledFilter mInstalledFilter;

    protected PackageManagerUtils mPMUtils;

    protected MergeRecyclerAdapter mAdapter;

    public static AppWatcherListFragment newInstance(int filterId) {
        AppWatcherListFragment frag = new AppWatcherListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_FILTER, filterId);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        AppWatcherActivity act = (AppWatcherActivity) activity;
        act.setQueryChangeListener(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        AppWatcherActivity act = (AppWatcherActivity) getActivity();
        act.setQueryChangeListener(null);
    }

    public void setListVisible(boolean visible, boolean animate) {
        if (visible) {
            if (animate) {
                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_out));
                if (mAdapter.getItemCount() == 0){
                    mEmptyView.startAnimation(AnimationUtils.loadAnimation(
                            getActivity(), android.R.anim.fade_in));
                } else {
                    mListView.startAnimation(AnimationUtils.loadAnimation(
                            getActivity(), android.R.anim.fade_in));
                }
            }
            mProgressContainer.setVisibility(View.GONE);
            if (mAdapter.getItemCount() == 0){
                mEmptyView.setVisibility(View.VISIBLE);
                mListView.setVisibility(View.INVISIBLE);
            } else {
                mEmptyView.setVisibility(View.GONE);
                mListView.setVisibility(View.VISIBLE);
            }
        } else {
            if (animate) {
                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_in));
                mListView.startAnimation(AnimationUtils.loadAnimation(
                        getActivity(), android.R.anim.fade_out));
            }
            mProgressContainer.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.INVISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }

    public void setListVisible(boolean shown) {
        setListVisible(shown, true);
    }

    public void setListShownNoAnimation(boolean shown) {
        setListVisible(shown, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_applist, container, false);

        mListView = (RecyclerView) root.findViewById(android.R.id.list);
        mProgressContainer = root.findViewById(R.id.progress);
        mEmptyView = root.findViewById(android.R.id.empty);
        mEmptyView.setVisibility(View.GONE);

        return root;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        mPMUtils = new PackageManagerUtils(getActivity().getPackageManager());

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false);
        mListView.setLayoutManager(layoutManager);

        Resources r = getResources();

        // Create an empty adapter we will use to display the loaded data.

        mAdapter = new MergeRecyclerAdapter();
        mAdapter.addAdapter(ADAPTER_WATCHLIST, new ListCursorAdapterWrapper(getActivity(), mPMUtils, this));

        mListView.setAdapter(mAdapter);

        // Start out with a progress indicator.
        setListVisible(false);

        setupFilter(getArguments().getInt(ARG_FILTER));
        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AppListCursorLoader(getActivity(), mTitleFilter, mInstalledFilter);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        ListCursorAdapterWrapper watchlistAdapter = ((ListCursorAdapterWrapper) mAdapter.getAdapter(ADAPTER_WATCHLIST));
        watchlistAdapter.swapData(data);
        if (mInstalledFilter == null) {
            int newCount = ((AppListCursorLoader) loader).getNewCount();
            watchlistAdapter.setNewAppsCount(newCount);
        } else {
            int newCount = mInstalledFilter.getNewCount();
            watchlistAdapter.setNewAppsCount(newCount);
        }

        // The list should now be shown.
        if (isResumed()) {
            setListVisible(true);
        } else {
            setListShownNoAnimation(true);
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        ListCursorAdapterWrapper watchlistAdapter = ((ListCursorAdapterWrapper) mAdapter.getAdapter(ADAPTER_WATCHLIST));
        watchlistAdapter.swapData(null);
    }

    private void setupFilter(int filterId) {
        if (filterId == Filters.TAB_INSTALLED) {
            mInstalledFilter = new InstalledFilter(true, mPMUtils);
        } else if (filterId == Filters.TAB_UNINSTALLED) {
            mInstalledFilter = new InstalledFilter(false, mPMUtils);
        } else {
            mInstalledFilter = null;
        }
    }

    @Override
    public void onQueryTextChanged(String newQuery) {
        String newFilter = !TextUtils.isEmpty(newQuery) ? newQuery : "";
        if (!TextUtils.equals(newFilter, mTitleFilter)) {
            mTitleFilter = newFilter;
            getLoaderManager().restartLoader(0, null, this);
        }
    }



    @Override
    public void onRefresh() {
        ((AppWatcherActivity) getActivity()).requestRefresh();
    }

    @Override
    public void onIconClick(AppInfo app) {
        String pkgName = app.getPackageName();
        boolean isInstalled = mPMUtils.isAppInstalled(app.getPackageName());
        if (isInstalled) {
            Intent appInfo = IntentUtils.createApplicationDetailsIntent(app.getPackageName());
            startActivity(appInfo);
        }
        if (BuildConfig.DEBUG) {
            AppLog.d(pkgName);
            Toast.makeText(getActivity(), pkgName, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onItemClick(AppInfo app) {
        Intent intent = new Intent(getActivity(), ChangelogActivity.class);
        intent.putExtra(ChangelogActivity.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(ChangelogActivity.EXTRA_DETAILS_URL, app.getDetailsUrl());
        startActivity(intent);
    }
}

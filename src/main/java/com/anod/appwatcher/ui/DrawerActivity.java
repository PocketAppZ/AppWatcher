package com.anod.appwatcher.ui;

import android.accounts.Account;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anod.appwatcher.Preferences;
import com.anod.appwatcher.R;

/**
 * @author alex
 * @date 2014-08-07
 */
abstract public class DrawerActivity extends ToolbarActivity {
    DrawerLayout mDrawerLayout;
    private TextView mAccountNameView;
    protected NavigationView mNavigationView;

    protected void setupDrawer() {
        setupToolbar();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        setupDrawerContent(mNavigationView);

    }

    private void setupDrawerContent(NavigationView navigationView) {
        View headerView = navigationView.getHeaderView(0);

        mAccountNameView = ((TextView) headerView.findViewById(R.id.account_name));
        LinearLayout changeAccount = (LinearLayout) headerView.findViewById(R.id.account_change);
        changeAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAccountChooseClick();
            }
        });

        Preferences pref = new Preferences(this);
        long time = pref.getLastUpdateTime();

        TextView lastUpdateView = (TextView) headerView.findViewById(R.id.last_update);
        if (time > 0) {
            String lastUpdate = getString(R.string.last_update, DateUtils.getRelativeDateTimeString(this, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
            lastUpdateView.setText(lastUpdate);
            lastUpdateView.setVisibility(View.VISIBLE);
        } else {
            lastUpdateView.setVisibility(View.GONE);/**/
        }

        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        menuItem.setChecked(true);
                        onOptionsItemSelected(menuItem);
                        //mDrawerLayout.closeDrawers();
                        return true;
                    }
                });
    }

    protected abstract void onAccountChooseClick();

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    protected void setDrawerAccount(Account account) {
        if (account == null) {
            mAccountNameView.setText(R.string.choose_an_account);
        } else {
            mAccountNameView.setText(account.name);
        }
    }
}

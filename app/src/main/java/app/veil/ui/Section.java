package app.veil.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import app.veil.R;

/** The rows of the section list, in order. Each one owns the page on the right. */
public enum Section {
    AIRPLAY(R.string.section_airplay, R.drawable.ic_airplay) {
        @Override
        public Fragment newFragment() {
            return PlaceholderFragment.create(R.string.section_airplay, R.string.placeholder_airplay);
        }
    },
    DIM(R.string.section_dim, R.drawable.ic_moon) {
        @Override
        public Fragment newFragment() {
            return new app.veil.dim.DimFragment();
        }
    },
    NETWORK(R.string.section_network, R.drawable.ic_wifi) {
        @Override
        public Fragment newFragment() {
            return PlaceholderFragment.create(R.string.section_network, R.string.placeholder_network);
        }
    },
    ABOUT(R.string.section_about, R.drawable.ic_info) {
        @Override
        public Fragment newFragment() {
            return new AboutFragment();
        }
    };

    @StringRes
    public final int title;
    @DrawableRes
    public final int icon;

    Section(@StringRes int title, @DrawableRes int icon) {
        this.title = title;
        this.icon = icon;
    }

    public abstract Fragment newFragment();
}

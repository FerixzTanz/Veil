package app.veil.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.veil.R;

/** Stands in for a section whose feature lands in a later step. */
public class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_BODY = "body";

    public static PlaceholderFragment create(int titleRes, int bodyRes) {
        PlaceholderFragment f = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, titleRes);
        args.putInt(ARG_BODY, bodyRes);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_placeholder, container, false);
        Bundle args = requireArguments();
        ((TextView) v.findViewById(R.id.placeholder_title)).setText(args.getInt(ARG_TITLE));
        ((TextView) v.findViewById(R.id.placeholder_body)).setText(args.getInt(ARG_BODY));
        return v;
    }
}

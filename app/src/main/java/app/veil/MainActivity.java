package app.veil;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.veil.ui.Section;
import app.veil.ui.SectionAdapter;

/**
 * The shell every page lives in: a 64 dp title bar, a 280 dp section list on the
 * left and the section's content on the right. Switching sections does not enter
 * the back stack; the list is always visible, so a section is a tab, not a page.
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_SECTION = "section";

    private TextView title;
    private SectionAdapter adapter;
    private Section current;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        title = findViewById(R.id.title_text);
        View back = findViewById(R.id.title_back);
        back.setOnClickListener(v -> finish());

        RecyclerView list = findViewById(R.id.section_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SectionAdapter(this::show);
        list.setAdapter(adapter);

        Section start = Section.AIRPLAY;
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_SECTION);
            if (saved != null) {
                start = Section.valueOf(saved);
            }
        }
        show(start, savedInstanceState == null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (current != null) {
            outState.putString(STATE_SECTION, current.name());
        }
    }

    private void show(Section section) {
        show(section, true);
    }

    private void show(Section section, boolean replaceFragment) {
        current = section;
        adapter.select(section);
        title.setText(section.title);
        if (replaceFragment) {
            Fragment fragment = section.newFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.section_content, fragment, section.name())
                    .commit();
        }
    }
}

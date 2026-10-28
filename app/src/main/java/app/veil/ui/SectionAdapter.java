package app.veil.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.veil.R;

/** The 280 dp list on the left. The selected row sits on a sunken grey block. */
public final class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.Row> {

    public interface OnSelect {
        void onSelect(Section section);
    }

    private final OnSelect onSelect;
    private final Section[] sections = Section.values();
    private Section selected = Section.AIRPLAY;

    public SectionAdapter(OnSelect onSelect) {
        this.onSelect = onSelect;
    }

    public void select(Section section) {
        if (selected == section) {
            return;
        }
        int before = selected.ordinal();
        selected = section;
        notifyItemChanged(before);
        notifyItemChanged(section.ordinal());
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_section, parent, false);
        return new Row(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Row row, int position) {
        Section section = sections[position];
        row.icon.setImageResource(section.icon);
        row.label.setText(section.title);
        row.itemView.setActivated(section == selected);
        row.itemView.setOnClickListener(v -> onSelect.onSelect(section));
    }

    @Override
    public int getItemCount() {
        return sections.length;
    }

    static final class Row extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        Row(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.section_icon);
            label = itemView.findViewById(R.id.section_label);
        }
    }
}

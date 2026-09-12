package com.tonya.dzcscale;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.tonya.dzcscale.history.MeasurementHistoryRepository;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

/** Read-only local history of completed measurement sessions. */
public class HistoryActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_history);

        View root = findViewById(R.id.historyRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, i) -> {
            Insets x = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), x.top, v.getPaddingRight(), x.bottom);
            return i;
        });

        findViewById(R.id.historyBack).setOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.historyContainer);
        TextView emptyText = findViewById(R.id.emptyHistoryText);

        List<MeasurementHistoryRepository.Entry> entries = MeasurementHistoryRepository.load(this);
        if (entries.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

            for (MeasurementHistoryRepository.Entry e : entries) {
                MaterialCardView card = new MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewElevatedStyle);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 24);
                card.setLayoutParams(params);
                card.setRadius(dpToPx(16));
                card.setCardElevation(dpToPx(2));
                card.setStrokeColor(getColor(R.color.dzc_card_outline));
                card.setStrokeWidth(dpToPx(1));

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int padding = dpToPx(16);
                layout.setPadding(padding, padding, padding, padding);

                TextView dateText = new TextView(this);
                dateText.setText(df.format(e.time));
                dateText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
                dateText.setTextColor(getColor(R.color.dzc_primary));

                TextView weightText = new TextView(this);
                weightText.setText(String.format(Locale.US, "%.2f kg", e.weight));
                weightText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
                weightText.setTextColor(getColor(R.color.dzc_on_surface));
                weightText.setPadding(0, dpToPx(4), 0, dpToPx(6));

                TextView detailsText = new TextView(this);
                detailsText.setText(String.format(Locale.US,
                        "Body fat  %.1f%%   •   BMI  %.1f\nImpedance  %.1f Ω   •   Quality  %d/100",
                        e.bodyFat, e.bmi, e.impedance, e.quality));
                detailsText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                detailsText.setTextColor(getColor(R.color.dzc_on_surface_variant));

                layout.addView(dateText);
                layout.addView(weightText);
                layout.addView(detailsText);
                card.addView(layout);
                container.addView(card);
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

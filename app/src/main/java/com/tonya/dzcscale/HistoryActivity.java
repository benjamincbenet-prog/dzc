package com.tonya.dzcscale;
import android.os.Bundle; import android.view.View; import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity; import androidx.core.graphics.Insets; import androidx.core.view.ViewCompat; import androidx.core.view.WindowCompat; import androidx.core.view.WindowInsetsCompat;
import com.tonya.dzcscale.history.MeasurementHistoryRepository; import java.text.DateFormat; import java.util.Locale;
/** Read-only local history of completed measurement sessions. */
public class HistoryActivity extends AppCompatActivity {
 @Override public void onCreate(Bundle b){super.onCreate(b); WindowCompat.setDecorFitsSystemWindows(getWindow(),false); setContentView(R.layout.activity_history); View root=findViewById(R.id.historyRoot); ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets x=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(v.getPaddingLeft(),x.top,v.getPaddingRight(),x.bottom);return i;}); findViewById(R.id.historyBack).setOnClickListener(v->finish()); TextView list=findViewById(R.id.historyList); StringBuilder s=new StringBuilder(); DateFormat df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);
  for(MeasurementHistoryRepository.Entry e:MeasurementHistoryRepository.load(this)){if(s.length()>0)s.append("\n\n");s.append(df.format(e.time)).append("\n").append(String.format(Locale.US,"%.2f kg   %.1f%% body fat\nBMI %.1f   Impedance %.1f Ω   Quality %d/100",e.weight,e.bodyFat,e.bmi,e.impedance,e.quality));}
  list.setText(s.length()==0?"No measurements yet. Completed weighings are saved here automatically.":s.toString()); }
}
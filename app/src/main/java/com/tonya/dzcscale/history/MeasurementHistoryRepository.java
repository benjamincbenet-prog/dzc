package com.tonya.dzcscale.history;

import android.content.Context;
import android.content.SharedPreferences;
import com.tonya.dzcscale.model.BodyMetrics;
import com.tonya.dzcscale.model.Measurement;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Stores a compact local measurement history. Health Connect sync is optional and never
 * replaces this history, so a completed weighing remains visible even when sync is unavailable. */
public final class MeasurementHistoryRepository {
    private static final String PREFS = "measurement_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 100;
    private MeasurementHistoryRepository() {}

    public static void add(Context context, Measurement m, BodyMetrics x) {
        try {
            SharedPreferences p=context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray old=new JSONArray(p.getString(KEY_ITEMS,"[]"));
            JSONArray out=new JSONArray();
            JSONObject item=new JSONObject();
            item.put("time",m.timeMillis()); item.put("weight",x.weightKg());
            item.put("bodyFat",x.bodyFatPercent()); item.put("impedance",x.impedanceOhm());
            item.put("bmi",x.bmi()); item.put("quality",x.qualityScore());
            item.put("model",x.primaryModel());
            out.put(item);
            for(int i=0;i<old.length() && i<MAX_ITEMS-1;i++) out.put(old.getJSONObject(i));
            p.edit().putString(KEY_ITEMS,out.toString()).apply();
        } catch(Exception ignored) { }
    }
    public static List<Entry> load(Context context) {
        List<Entry> result=new ArrayList<>();
        try { JSONArray a=new JSONArray(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_ITEMS,"[]"));
            for(int i=0;i<a.length();i++){ JSONObject o=a.getJSONObject(i); result.add(new Entry(o.optLong("time"),o.optDouble("weight"),o.optDouble("bodyFat"),o.optDouble("impedance"),o.optDouble("bmi"),o.optInt("quality"),o.optString("model"))); }
        } catch(Exception ignored) { }
        return result;
    }
    public static final class Entry {
        public final long time; public final double weight, bodyFat, impedance, bmi; public final int quality; public final String model;
        Entry(long t,double w,double f,double z,double b,int q,String m){time=t;weight=w;bodyFat=f;impedance=z;bmi=b;quality=q;model=m;}
    }
}
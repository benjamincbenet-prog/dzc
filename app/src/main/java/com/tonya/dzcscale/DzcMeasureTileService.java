package com.tonya.dzcscale;
import android.app.PendingIntent; import android.content.Intent; import android.os.Build; import android.service.quicksettings.TileService;
/** Quick Settings entry point. It opens a normal measurement session; BLE stays owned by MainActivity. */
public class DzcMeasureTileService extends TileService {
 @Override public void onClick(){ Intent i=new Intent(this,MainActivity.class).setAction(MainActivity.ACTION_MEASURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); if(Build.VERSION.SDK_INT>=34){PendingIntent p=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); startActivityAndCollapse(p);}else{startActivityAndCollapse(i);} }
}
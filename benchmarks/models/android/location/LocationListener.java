package android.location;

import android.os.Bundle;

public interface LocationListener {
    public abstract void onLocationChanged(Location location);
    public abstract void onProviderDisabled(String provider);
    public abstract void onProviderEnabled(String provider);
    public abstract void onStatusChanged(String s, int i, Bundle bundle);
}
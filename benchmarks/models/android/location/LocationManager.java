package android.location;

import android.location.LocationProvider;

public final class LocationManager {
    public final static String GPS_PROVIDER = "GPS_PROVIDER";
    public final static String NETWORK_PROVIDER = "NETWORK_PROVIDER";
    public final static String PASSIVE_PROVIDER = "PASSIVE_PROVIDER";

    public final static int GPS_PROVIDER_ID = 1;
    public final static int NETWORK_PROVIDER_ID = 2;
    public final static int PASSIVE_PROVIDER_ID = 3;

    private final static boolean providerEnabled = nd$bool();

    private static LocationProvider gpsProvider = new LocationProvider(GPS_PROVIDER);
    private static LocationProvider networkProvider = new LocationProvider(NETWORK_PROVIDER);
    private static LocationProvider passiveProvider = new LocationProvider(PASSIVE_PROVIDER);

    private static LocationProvider nd$LocationProvider() {
        return null;
    }

    private static boolean nd$bool()
    {
        return true;
    }

    public void requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener) {

    }

    public void requestLocationUpdates(int provider, long minTime, int minDistance, LocationListener listener) {

    }

    public void removeUpdates(LocationListener l) {

    }

    public Location getLastKnownLocation(String provider) {
        return new Location(provider);
    }

    public Location getLastKnownLocation(int provider) {
        return new Location(null);
    }

    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        return GPS_PROVIDER;
    }

    public int getBestProviderId(Criteria criteria, boolean enabledOnly) {
        return GPS_PROVIDER_ID;
    }

    public boolean isProviderEnabled(String provider) {
        return providerEnabled;
    }

    public boolean isProviderEnabled(int provider) {
        return providerEnabled;
    }

    public LocationProvider getProvider(String name) {
        if(name.equals(GPS_PROVIDER)) {
            return gpsProvider;
        }
        else if(name.equals(NETWORK_PROVIDER)) {
            return networkProvider;
        }
        else if(name.equals(PASSIVE_PROVIDER)) {
            return passiveProvider;
        }
        return null;
    }

    public LocationProvider getProvider(int name) {
        if(name == GPS_PROVIDER_ID) {
            return gpsProvider;
        }
        else if(name == NETWORK_PROVIDER_ID) {
            return networkProvider;
        }
        else if(name == PASSIVE_PROVIDER_ID) {
            return passiveProvider;
        }
        return null;
    }
}
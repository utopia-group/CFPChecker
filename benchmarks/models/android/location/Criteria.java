package android.location;

public final class Criteria {
    public static final int ACCURACY_COARSE = 1;
    public static final int ACCURACY_FINE = 2;
    public static final int ACCURACY_HIGH = 3;
    public static final int ACCURACY_LOW = 4;
    public static final int ACCURACY_MEDIUM = 5;
    public static final int NO_REQUIREMENT = 6;
    public static final int POWER_HIGH = 7;
    public static final int POWER_LOW = 8;
    public static final int POWER_MEDIUM = 9;

    public Criteria() {

    }

    public void setAltitudeRequired(boolean altitudeRequired) {

    }

    public void setBearingRequired(boolean bearingRequired) {

    }

    public void setSpeedRequired(boolean speedRequired) {

    }

    public void setCostAllowed(boolean costAllowed) {

    }

    public void setPowerRequirement(int level) {

    }

    public void setAccuracy(int accuracy) {

    }
}

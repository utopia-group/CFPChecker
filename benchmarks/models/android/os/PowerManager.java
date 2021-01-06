package android.os;

public final class PowerManager {

    public static final int PARTIAL_WAKE_LOCK = 0x00000001;

    public static final int SCREEN_DIM_WAKE_LOCK = 0x00000006;

    public static final int SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a;

    public static final int FULL_WAKE_LOCK = 0x0000001a;

    public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 0x00000020;

    public static final int ACQUIRE_CAUSES_WAKEUP = 0x10000000;

    public static final int ON_AFTER_RELEASE = 0x20000000;

    public static final int WAIT_FOR_PROXIMITY_NEGATIVE = 1;

    public static final int BRIGHTNESS_ON = 255;

    public static final int BRIGHTNESS_OFF = 0;

    public static final int USER_ACTIVITY_EVENT_OTHER = 0;

    public static final int USER_ACTIVITY_EVENT_BUTTON = 1;

    public static final int USER_ACTIVITY_EVENT_TOUCH = 2;

    public static final int USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS = 1 << 0;

    public static final int GO_TO_SLEEP_REASON_USER = 0;

    public static final int GO_TO_SLEEP_REASON_DEVICE_ADMIN = 1;

    static public final class WakeLock
    {
        public int mCount = 0;

        public void acquire()
        {
            mCount++;
        }

        public void release()
        {
            mCount--;
        }

        public boolean isHeld()
        {
            return mCount > 0;
        }

        public void setReferenceCounted(boolean refCounted) {}
    }

    public WakeLock newWakeLock(int levelAndFlags)
    {
        return new WakeLock();
    }
}
package android.net.wifi;

public class WifiManager
{
    public static final int WIFI_MODE_FULL_HIGH_PERF = 3;

    public class WifiLock
    {
        public void acquire()
        {

        }

        public void release()
        {

        }

        public void setReferenceCounted(boolean refCounted) {}
    }

    public WifiLock createWifiLock(int mode)
    {
        return new WifiLock();
    }

    private static WifiLock nd$WifiLock()
    {
        return null;
    }
}
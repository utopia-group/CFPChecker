package singlereceiver;

import android.os.PowerManager;
import android.content.Context;
import android.app.Activity;

public class Test extends Activity
{
    private PowerManager.WakeLock createWakeLock()
    {
        PowerManager manager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakelock = manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK);
        return wakelock;
    }

    private void acquire(PowerManager.WakeLock l)
    {
        if (l != null)
            l.acquire();
    }

    private void release(PowerManager.WakeLock l)
    {
        if (l != null && l.isHeld())
            l.release();
    }

    private static boolean nd$boolean()
    {
        return true;
    }

    public static void main(String[] args)
    {
        Test t = new Test();

        PowerManager.WakeLock l = t.createWakeLock();

        t.acquire(l);

        t.release(l);
    }
}
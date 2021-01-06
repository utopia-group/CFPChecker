package recursivelock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

class Simple
{
    static void foo(int n, ReentrantLock l) {
//        if (n >= 0) {
            l.lock();

//            foo(n - 1, l);

            l.unlock();
//        }
        return;
    }

    public static void main(String[] args) {
        Random r = new Random();
        int n = r.nextInt();

        for(int i = 0; i < n; i++)
        {
            ReentrantLock l = new ReentrantLock();
            l.lock();
            l.unlock();
            // TODO: There is a bug when do this with a call.
//            foo(n, l);
        }

        return;
    }
}

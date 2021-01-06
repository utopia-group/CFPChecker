package recursivelock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    static void foo(int n) {
        if(n >= 0) {
            l.lock();
            foo(n - 1);
            l.unlock();
        }
    }

    public static void main(String[] args)
    {
        Random r = new Random();
        int n = r.nextInt();

        while(n >= 0) {
            foo(n);
            n--;
        }

        return;
    }
}

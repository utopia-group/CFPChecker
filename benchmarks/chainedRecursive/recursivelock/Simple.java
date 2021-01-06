package recursivelock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    static void foo2(int n) {
        if(n >= 0) {
            l.lock();
            foo2(n - 1);
            l.unlock();
        }
    }

    static void foo1(int n) {
        if(n >= 0) {
            l.lock();
            foo1(n - 1);
            l.unlock();
        }
    }

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

        foo(n);
        foo1(n);
        foo2(n);

        return;
    }
}

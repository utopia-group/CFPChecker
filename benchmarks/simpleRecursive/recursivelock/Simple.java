package recursivelock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    static void foo(int n)
    {
        if (n >= 0)
        {
            l.lock();
            n = n - 1;
            foo(n);
            l.unlock();
        }
        else
        {
            //skip;
        }
        return;
    }

    static void main(String[] args)
    {
        Random r = new Random();
        int n = r.nextInt();
        if (n >= 0)
            foo(n);
        else
        {
            //skip;
        }
        return;
    }
}

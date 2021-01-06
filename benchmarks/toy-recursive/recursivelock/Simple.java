package recursivelock;

import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    static ReentrantLock l = new ReentrantLock();
    static int n = 10;

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
        if (n >= 0)
            foo(n);
        else
        {
            //skip;
        }
        return;
    }
}

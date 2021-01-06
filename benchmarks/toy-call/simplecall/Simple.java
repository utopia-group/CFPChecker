package simplecall;

import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    static void main(String[] args)
    {
        foo(l);

        bar(l);
    }

    static void foo(ReentrantLock l1)
    {
        l1.lock();
    }

    static void bar(ReentrantLock l1)
    {
        l1.unlock();
    }

}

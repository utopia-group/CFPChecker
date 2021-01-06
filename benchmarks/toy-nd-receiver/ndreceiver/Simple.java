package ndreceiver;

import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    ReentrantLock l;

    private static ReentrantLock nd$Lock()
    {
        return null;
    }

    void foo()
    {
        this.l = nd$Lock();
        l.lock();
    }

    void bar()
    {
        l.unlock();
        l = null;
    }

    static void main(String[] args)
    {

        Simple s = new Simple();
        s.foo();
        s.bar();
    }
}
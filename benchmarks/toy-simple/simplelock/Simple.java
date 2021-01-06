package simplelock;

import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    static void main(String[] args)
    {
        l.lock();
        l.unlock();
    }
}

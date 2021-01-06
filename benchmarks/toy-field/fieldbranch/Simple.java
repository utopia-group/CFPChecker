package fieldbranch;
import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    private static boolean nd$boolean()
    {
        return false;
    }

    boolean p;

    void foo()
    {
        p = nd$boolean();
        if (p)
            l.lock();
    }

    void bar()
    {
        if (p)
            l.unlock();
    }

    static void main(String[] args)
    {
        Simple s = new Simple();
        s.foo();
        s.bar();
    }
}
package recursivelock;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    static ReentrantLock l = new ReentrantLock();

    Simple next;

    Simple()
    {   
        next = null;
    }   

    Simple(Simple next) {   
        this.next = next;
    }  

    static void foo(Simple list) {
        if(list != null) {
            l.lock();
            foo(list.next);
            l.unlock();
        }
        return;
    }

    public static void main(String[] args)
    {
        Simple n1 = new Simple();
        Simple n2 = new Simple(n1);
        Simple n3 = new Simple(n2);

        foo(n3);

        return;
    }
}

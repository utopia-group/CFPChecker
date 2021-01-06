package recursivelock;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

class Simple
{
    static ReentrantLock l = new ReentrantLock();
    static Random r = new Random();

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
        int n = r.nextInt();
        Simple list = new Simple();

        for(int i = 0; i < n; i++) {
            list = new Simple(list);
        }

        foo(list);

        return;
    }
}

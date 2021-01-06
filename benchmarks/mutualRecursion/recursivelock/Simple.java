package recursivelock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

class Simple
{
    static ReentrantLock l = new ReentrantLock();
    static Random r = new Random();

//    static void foo4(int n) {
//        if(n >= 0) {
//            l.lock();
//
//            int toCall = r.nextInt();
//            if(toCall == 1) {
//                foo1(n - 1);
//            }
//            else if(toCall == 2) {
//                foo2(n - 1);
//            }
//            else if(toCall == 3) {
//                foo3(n - 1);
//            }
//            else if(toCall == 4) {
//                foo4(n - 1);
//            }
//            else {
//                foo(n - 1);
//            }
//
//            l.unlock();
//        }
//    }
//
//    static void foo3(int n) {
//        if(n >= 0) {
//            l.lock();
//
//            int toCall = r.nextInt();
//            if(toCall == 1) {
//                foo1(n - 1);
//            }
//            else if(toCall == 2) {
//                foo2(n - 1);
//            }
//            else if(toCall == 3) {
//                foo3(n - 1);
//            }
//            else if(toCall == 4) {
//                foo4(n - 1);
//            }
//            else {
//                foo(n - 1);
//            }
//
//            l.unlock();
//        }
//    }

    static void foo2(int n) {
        if(n >= 0) {
            l.lock();
            
            int toCall = r.nextInt();
            if(toCall == 1) {
                foo1(n - 1);
            }
            else if(toCall == 2) {
                foo2(n - 1);
            }
            else {
                foo(n - 1);
            }

            l.unlock();
        }
    }

    static void foo1(int n) {
        if(n >= 0) {
            l.lock();
            
            int toCall = r.nextInt();
            if(toCall == 1) {
                foo1(n - 1);
            }
            else if(toCall == 2) {
                foo2(n - 1);
            }
            else {
                foo(n - 1);
            }

            l.unlock();
        }
    }

    static void foo(int n) {
        if(n >= 0) {
            l.lock();
            
            int toCall = r.nextInt();
            if(toCall == 1) {
                foo1(n - 1);
            }
            else if(toCall == 2) {
                foo2(n - 1);
            }
            else {
                foo(n - 1);
            }

            l.unlock();
        }
    }

    public static void main(String[] args)
    {
        int n = r.nextInt();

        foo(n);

        return;
    }
}

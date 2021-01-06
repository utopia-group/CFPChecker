import java.util.concurrent.locks.*;

//based off of post: https://stackoverflow.com/questions/9189229/tried-nested-locks-but-still-facing-the-deadlock

public class LockCountPractice {
    private static final ReentrantLock lock = new ReentrantLock();

    static private void methodThree(ReentrantLock l) {
        l.lock();
    }

    static private void methodTwo(ReentrantLock l) {
        l.lock();
        methodThree(l);
    }

    static public void methodOne(ReentrantLock l) {
        l.lock();
        methodTwo(l);
        l.unlock();
    }

    public static void main(String [] args) {
        methodOne(lock);
    }
}

/*public class LockCountPractice {
    private static final ReentrantLock l1 = new ReentrantLock();
    private static final ReentrantLock l2 = new ReentrantLock();
    private static final ReentrantLock l3 = new ReentrantLock();

    static private void methodThree() {
        l3.lock();
    }

    static private void methodTwo() {
        l2.lock();
        methodThree();
    }

    static public void methodOne() {
        l1.lock();
        methodTwo();

        //does it detect out of order unlocking?
        l1.unlock();
        l2.unlock();
        l3.unlock();
    }

    public static void main(String [] args) {
        methodOne();
    }
}*/

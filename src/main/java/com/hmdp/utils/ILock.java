package com.hmdp.utils;

/**
 * ClassName: ILock
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Chao Fang
 * @Create 2024/7/15 19:47
 * @Version 1.0
 */
public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}

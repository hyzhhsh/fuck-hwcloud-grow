package com.hesihai.fuckhwcloudgrow.service.task;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 自动化任务互斥锁：同一时刻只允许一批任务运行，避免同一个浏览器 profile 被并发打开。
 *
 * <p>使用可重入锁，编排器持锁期间同线程调用签到服务不会自锁。</p>
 */
@Component
public class AutomationLock {

    private final ReentrantLock lock = new ReentrantLock();

    public boolean tryAcquire() {
        return lock.tryLock();
    }

    public void release() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
package com.hesihai.fuckhwcloudgrow.util;

import java.time.Duration;
import java.util.function.Function;

/**
 * 可中断休眠工具。
 *
 * <p>统一“恢复中断标记 + 按调用方指定方式抛出异常”的样板代码。中断异常的
 * 类型与消息必须由调用方指定：不同调用链上中断的语义不同（登录流程直接失败
 * vs 网络异常触发浏览器降级），不能统一成一种异常。</p>
 */
public final class SleepUtils {

    private SleepUtils() {
    }

    /**
     * 休眠指定时长；被中断时恢复线程中断标记并抛出调用方指定的异常。
     *
     * @param duration         休眠时长，不足 1ms 按 1ms 处理，保证中断能被及时感知
     * @param exceptionFactory 中断时构造运行时异常的工厂，入参为捕获到的 InterruptedException
     */
    public static void sleep(Duration duration,
                             Function<InterruptedException, ? extends RuntimeException> exceptionFactory) {
        try {
            Thread.sleep(Math.max(1, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exceptionFactory.apply(e);
        }
    }
}

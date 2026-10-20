package com.hesihai.fuckhwcloudgrow.schedule;

import com.hesihai.fuckhwcloudgrow.model.RunTrigger;
import com.hesihai.fuckhwcloudgrow.model.SigninResult;
import com.hesihai.fuckhwcloudgrow.service.SigninBusyException;
import com.hesihai.fuckhwcloudgrow.service.SigninService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天定时执行签到。
 */
@Slf4j
@Component
public class DailySigninScheduler {

    private final SigninService signinService;

    public DailySigninScheduler(SigninService signinService) {
        this.signinService = signinService;
    }

    /**
     * 每日定时签到。
     *
     * <p>cron 与 zone 从 {@code hwcloud.grow.cron}/{@code hwcloud.grow.zone} 配置读取；
     * 已有任务在执行（{@link SigninBusyException}）时跳过本次，不重复排队。</p>
     */
    @Scheduled(cron = "${hwcloud.grow.cron:0 0 8 * * *}",
            zone = "${hwcloud.grow.zone:Asia/Shanghai}")
    public void runDaily() {
        log.info("定时签到任务开始");
        try {
            SigninResult result = signinService.run(RunTrigger.SCHEDULED);
            log.info("定时签到任务结束：{} - {}", result.getStatus(), result.getMessage());
        } catch (SigninBusyException e) {
            log.warn("已有签到任务在执行，跳过本次定时任务");
        } catch (Exception e) {
            log.error("定时签到任务异常：{}", e.getMessage(), e);
        }
    }
}

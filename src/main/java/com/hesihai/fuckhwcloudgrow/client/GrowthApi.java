package com.hesihai.fuckhwcloudgrow.client;

import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.MemberInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignSubmitOutcome;

/**
 * 成长中心签到接口的抽象。
 *
 * <p>Java HTTP 与 Playwright 页面内 fetch 各实现一份，签到编排逻辑只依赖此接口。</p>
 */
public interface GrowthApi extends AutoCloseable {

    /**
     * 查询个人资料。
     *
     * @return 个人资料，接口失败时抛出 {@link GrowthApiException}
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    MemberInfoResponse getMemberInfo();

    /**
     * 查询签到状态。
     *
     * @return 签到状态，接口失败时抛出 {@link GrowthApiException}
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    SignStatusResponse getSignStatus();

    /**
     * 查询成长值。
     *
     * @return 成长值余额，接口失败时抛出 {@link GrowthApiException}
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    BeansInfoResponse getBeans();

    /**
     * 提交签到。
     *
     * @param accountName 账号名，随请求提交给厂商
     * @return 提交结果，由调用方按 success/alreadySigned 分支处理
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    SignSubmitOutcome submitSign(String accountName);

    /**
     * 返回实现通道标识。
     *
     * @return "HTTP" 或 "PLAYWRIGHT"，用于结果记录
     */
    String source();

    @Override
    default void close() {
    }
}

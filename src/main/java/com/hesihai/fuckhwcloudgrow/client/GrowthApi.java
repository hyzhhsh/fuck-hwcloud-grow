package com.hesihai.fuckhwcloudgrow.client;

import com.hesihai.fuckhwcloudgrow.client.dto.BeansInfoResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignStatusResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.SignSubmitOutcome;
import com.hesihai.fuckhwcloudgrow.client.dto.TaskListResponse;
import com.hesihai.fuckhwcloudgrow.client.dto.UserInfoResponse;

import java.util.List;

/**
 * 成长中心接口的抽象。
 *
 * <p>当前实现为 Java HTTP 通道；任务编排逻辑只依赖此接口。</p>
 */
public interface GrowthApi extends AutoCloseable {

    /**
     * 查询当前登录用户信息，同时用于验证登录态。
     *
     * @return 用户信息，接口失败时抛出 {@link GrowthApiException}
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    UserInfoResponse getUserInfo();

    /**
     * 查询每日签到状态。
     *
     * @return 签到状态；未找到每日签到任务时返回 null，表示状态未知
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    SignStatusResponse getSignStatus();

    /**
     * 查询积分余额。
     *
     * @return 积分余额，接口失败时抛出 {@link GrowthApiException}
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    BeansInfoResponse getBeans();

    /**
     * 读取全部任务记录（自动翻页），用于校验其他每日任务的完成状态。
     *
     * @return 任务记录列表，接口失败时抛出 {@link GrowthApiException}
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    List<TaskListResponse.TaskRecord> listTasks();

    /**
     * 提交每日签到。
     *
     * @return 提交结果，由调用方结合积分变化判断是本次成功还是今日已签到
     * @throws GrowthApiException 接口调用失败或返回业务错误
     */
    SignSubmitOutcome submitSign();

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
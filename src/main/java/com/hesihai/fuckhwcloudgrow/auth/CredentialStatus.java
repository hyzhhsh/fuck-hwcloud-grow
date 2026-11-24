package com.hesihai.fuckhwcloudgrow.auth;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 一次凭据检查的结果：总体结论与各域探测明细。
 */
@Data
@Builder
public class CredentialStatus {

    public enum Level {
        VALID,
        INVALID,
        WAF_BLOCKED
    }

    public enum HostState {
        VALID,
        INVALID,
        WAF_BLOCKED,
        NETWORK_ERROR,
        NOT_PROBED
    }

    private Level level;
    private Map<String, HostState> hosts;
    private String message;
    private boolean loginAttempted;
    private boolean humanIntervention;

    public boolean valid() {
        return level == Level.VALID;
    }

    public static CredentialStatus valid(Map<String, HostState> hosts) {
        return CredentialStatus.builder()
                .level(Level.VALID)
                .hosts(hosts)
                .message("登录态有效")
                .build();
    }
}
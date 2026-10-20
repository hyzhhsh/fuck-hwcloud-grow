package com.hesihai.fuckhwcloudgrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用入口。
 *
 * <p>启用 {@code hwcloud.grow} 配置属性扫描与 {@link org.springframework.scheduling.annotation.Scheduled}
 * 定时任务，其余组件由 {@link SpringBootApplication} 自动装配。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FuckHwcloudGrowApplication {

    /**
     * 启动应用。
     *
     * @param args 命令行参数，未使用，全部交给 Spring Boot 处理
     */
    public static void main(String[] args) {
        SpringApplication.run(FuckHwcloudGrowApplication.class, args);
    }

}

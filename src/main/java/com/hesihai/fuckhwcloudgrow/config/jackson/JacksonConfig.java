package com.hesihai.fuckhwcloudgrow.config.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.jackson.ModelResolver;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置服务内外 JSON 的基础表达规则。
 *
 * <p>业务 DTO 使用 Java camelCase；线上 JSON 统一转换为 snake_case。Java Time 模块由 Spring Boot 注册，
 * 本配置只关闭时间戳输出并保持 ISO-8601 表达。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 定制 Spring Boot 管理的全局 ObjectMapper，避免各个 DTO 重复声明相同 JSON 规则。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * 让 springdoc 使用与接口 JSON 相同的字段命名策略生成 OpenAPI schema。
     *
     * <p>springdoc 使用 swagger-core 的模型解析器生成文档，默认解析器不会自动继承 Spring Boot
     * 的 Jackson 定制。将 Spring 管理的 ObjectMapper 传入解析器后，Knife4j 展示的字段名即可与实际
     * 请求和响应保持一致。</p>
     */
    @Bean
    public ModelConverter openApiModelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }
}

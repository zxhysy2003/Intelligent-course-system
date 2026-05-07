package com.sy.course_system.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Autowired
    private RecommendProperties recommendProperties;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(recommendProperties.getRegular().getConnectTimeoutMs());
        factory.setReadTimeout(recommendProperties.getRegular().getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}

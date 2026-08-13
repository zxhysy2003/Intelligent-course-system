package com.sy.course_system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.video-dir}")
    private String videoDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射视频资源
        registry.addResourceHandler("/videos/**")
                .addResourceLocations("file:" + videoDir + "/");
    }
    
}

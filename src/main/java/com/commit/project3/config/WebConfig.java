package com.commit.project3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // application.yaml의 file.upload.dir 값
    @Value("${file.upload.dir}")
    private String uploadDir;

    // 업로드 폴더의 파일을 /upload/파일명 주소로 볼 수 있게 연결
    // 업로드 폴더는 프로젝트 밖에 있어서, 이렇게 연결해야 화면에서 사진을 불러올 수 있음
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}

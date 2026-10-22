package com.commit.project3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
  @Value("${py.base.url}")
  private String pyUrl;

  //@Bean 어노테이션은 매서드 객체 위에, 메서드에서 리턴하는 데이터를 객체로 변환하는 기능
  @Bean
  public RestClient restClient(){
    //RestClient 객체를 생성 및 리턴하는 메서드
    //RestClient 객체는 스프링에서 다른 서버로 요청을 보낼 수 있는 기능을 제공하는 객체
    return RestClient.builder().baseUrl(pyUrl).build();
  }
}

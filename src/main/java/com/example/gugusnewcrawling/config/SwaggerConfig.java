package com.example.gugusnewcrawling.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger (SpringDoc OpenAPI) 설정 클래스
 * 
 * API 문서 자동 생성을 위한 Swagger UI 설정을 제공합니다.
 * 
 * 접속 URL: http://localhost:8080/swagger-ui.html
 * 
 * 설정 내용:
 * - API 제목: "guguscrawling Automation API"
 * - API 설명: 구구스 상품 스크래핑 자동화 프로젝트
 * - 버전: 1.0.0
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI 스펙 Bean 생성
     * 
     * Swagger UI에서 표시할 API 문서의 기본 구조를 생성합니다.
     * 
     * @return OpenAPI 인스턴스
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(apiInfo());
    }
    
    /**
     * API 정보 생성
     * 
     * Swagger UI에 표시될 API 제목, 설명, 버전을 설정합니다.
     * 
     * @return Info 인스턴스
     */
    private Info apiInfo() {
        return new Info()
                .title("guguscrawling Automation API")
                // 아래 부분을 한 줄로 수정해주세요.
                .description("구구스(gugus) 상품 스크래핑 자동화 프로젝트의 API 명세서입니다. html다운 후 로컬에서 파싱, 그 후 이미지파일을 전환하는 로직으로 진행됩니다.")
                .version("1.0.0");
    }
}

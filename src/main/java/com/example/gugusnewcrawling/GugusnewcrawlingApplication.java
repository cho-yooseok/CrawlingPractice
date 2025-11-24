package com.example.gugusnewcrawling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 구구스 크롤링 시스템의 메인 애플리케이션 클래스
 * 
 * 이 클래스는 Spring Boot 애플리케이션의 진입점 역할을 합니다.
 * - @EnableJpaAuditing: JPA 엔티티의 생성일시, 수정일시 자동 감지 기능 활성화
 *   (ScrapingQueue 엔티티의 @CreatedDate, @LastModifiedDate 자동 설정)
 */
@EnableJpaAuditing
@SpringBootApplication
public class GugusnewcrawlingApplication {

	/**
	 * 애플리케이션 실행 진입점
	 * 
	 * @param args 커맨드 라인 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(GugusnewcrawlingApplication.class, args);
	}
}
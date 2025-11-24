package com.example.gugusnewcrawling.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 스크래핑 대기열 엔티티
 * 
 * 크롤링할 상품 URL과 작업 상태를 관리하는 테이블입니다.
 * 각 단계별 작업(HTML 다운로드, 파싱, 이미지 다운로드)의 진행 상황을 추적합니다.
 */
@Entity
@Table(name = "scraping_queue")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class) // 생성/수정 시간 자동 감지를 위해 추가
public class ScrapingQueue {

    /** Primary Key, 자동 증가 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상품 페이지 URL (고유 제약 조건, 최대 512자) */
    @Column(unique = true, nullable = false, length = 512)
    private String url;

    /** 현재 작업 상태 (PENDING, PROCESSING, COMPLETED, PARSED, IMAGE_DOWNLOADED, FAILED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrapingStatus status;

    /** 레코드 생성 시간 (자동 설정, 수정 불가) */
    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /** 레코드 최종 수정 시간 (자동 설정) */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 빌더 패턴 생성자
     * 
     * @param url 상품 페이지 URL
     * @param status 초기 상태 (보통 PENDING)
     */
    @Builder
    public ScrapingQueue(String url, ScrapingStatus status) {
        this.url = url;
        this.status = status;
    }
}
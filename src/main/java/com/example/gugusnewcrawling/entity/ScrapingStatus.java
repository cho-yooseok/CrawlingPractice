package com.example.gugusnewcrawling.entity;

/**
 * 스크래핑 작업의 상태를 나타내는 열거형
 * 
 * 크롤링 프로세스의 전체 워크플로우:
 * PENDING → PROCESSING → COMPLETED → PARSED → IMAGE_DOWNLOADED
 *                                              ↓
 *                                            FAILED (에러 발생 시)
 */
public enum ScrapingStatus {
    /** URL 수집 완료, HTML 다운로드 대기 상태 */
    PENDING,
    
    /** 작업 처리 중 (HTML 다운로드, 파싱, 이미지 다운로드 등 진행 중) */
    PROCESSING,
    
    /** HTML 다운로드 완료, 파싱 대기 상태 */
    COMPLETED,
    
    /** HTML 파싱 및 상품 정보 저장 완료, 이미지 다운로드 대기 상태 */
    PARSED,
    
    /** 이미지 다운로드 및 로컬 경로 업데이트 완료 (최종 성공 상태) */
    IMAGE_DOWNLOADED,
    
    /** 처리 중 오류 발생 (어떤 단계에서든 실패 시) */
    FAILED
}
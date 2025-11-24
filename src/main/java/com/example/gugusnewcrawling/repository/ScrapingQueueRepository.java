package com.example.gugusnewcrawling.repository;

import com.example.gugusnewcrawling.entity.ScrapingQueue;
import com.example.gugusnewcrawling.entity.ScrapingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ScrapingQueue 엔티티를 위한 Spring Data JPA 레포지토리
 * 
 * 스크래핑 대기열의 CRUD 작업과 상태별 조회를 제공합니다.
 */
@Repository
public interface ScrapingQueueRepository extends JpaRepository<ScrapingQueue, Long> {

    /**
     * URL 중복 확인을 위한 메서드
     * 
     * @param url 확인할 URL
     * @return URL이 존재하면 true, 없으면 false
     */
    boolean existsByUrl(String url);

    /**
     * 특정 상태의 아이템을 개수만큼 조회하는 메서드
     * 
     * 배치 처리 시 특정 상태의 작업을 일정 개수만큼 가져올 때 사용합니다.
     * 예: PENDING 상태의 작업 100개를 가져와서 HTML 다운로드 처리
     * 
     * @param status 조회할 상태
     * @param pageable 페이징 정보 (크기, 정렬 등)
     * @return 해당 상태의 ScrapingQueue 리스트
     */
    List<ScrapingQueue> findByStatus(ScrapingStatus status, Pageable pageable);

    /**
     * 각 상태별 개수를 확인하는 메서드
     * 
     * 작업 현황 조회(/status API)에서 사용됩니다.
     * 
     * @param status 조회할 상태
     * @return 해당 상태의 레코드 개수
     */
    long countByStatus(ScrapingStatus status);
    
    /**
     * 마지막으로 저장된 URL을 찾는 메서드
     * 
     * 가장 최근에 추가된 작업을 확인할 때 사용됩니다.
     * 
     * @return 가장 최근에 생성된 ScrapingQueue (없으면 Optional.empty())
     */
    Optional<ScrapingQueue> findFirstByOrderByCreatedAtDesc();
}
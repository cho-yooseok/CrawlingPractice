package com.example.gugusnewcrawling.repository;

import com.example.gugusnewcrawling.entity.Product;
import com.example.gugusnewcrawling.entity.ScrapingQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Product 엔티티를 위한 Spring Data JPA 레포지토리
 * 
 * 상품 정보의 CRUD 작업을 제공합니다.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    /**
     * ScrapingQueue를 통해 Product를 조회하는 메서드
     * 
     * HTML 파싱 시 기존 상품 정보가 있는지 확인하거나,
     * 이미지 다운로드 시 해당 상품 정보를 조회할 때 사용됩니다.
     * 
     * @param scrapingQueue 연결된 ScrapingQueue
     * @return 해당 ScrapingQueue에 연결된 Product (없으면 Optional.empty())
     */
    Optional<Product> findByScrapingQueue(ScrapingQueue scrapingQueue);
}
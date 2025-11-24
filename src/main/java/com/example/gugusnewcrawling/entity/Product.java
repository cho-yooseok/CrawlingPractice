package com.example.gugusnewcrawling.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상품 정보 엔티티
 * 
 * HTML 파싱을 통해 추출한 상품 정보를 저장하는 테이블입니다.
 * ScrapingQueue와 1:1 관계로 연결되어 있으며, 
 * 각 상품의 브랜드, 이름, 가격, 이미지 정보를 저장합니다.
 */
@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    /** Primary Key, 자동 증가 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 스크래핑 큐와의 1:1 관계 (지연 로딩, 고유 제약) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", unique = true)
    private ScrapingQueue scrapingQueue;

    /** 브랜드명 (최대 255자) */
    @Column(length = 255)
    private String brand;

    /** 상품명 (최대 512자) */
    @Column(length = 512)
    private String name;

    /** 구구스 상품번호 (고유 제약, 필수, null 불가) */
    @Column(unique = true, nullable = false)
    private String gugusProductNo;

    /** 상품 가격 */
    private Long price;

    /** 이미지 URL 1 (경로가 길어질 수 있으므로 길이를 1024로 설정) */
    @Column(length = 1024)
    private String imageUrl1;
    
    /** 이미지 URL 2 */
    @Column(length = 1024)
    private String imageUrl2;
    
    /** 이미지 URL 3 */
    @Column(length = 1024)
    private String imageUrl3;
    
    /** 이미지 URL 4 */
    @Column(length = 1024)
    private String imageUrl4;
    
    /** 이미지 URL 5 */
    @Column(length = 1024)
    private String imageUrl5;
    
    /** 이미지 URL 6 (최대 6개까지 저장 가능) */
    @Column(length = 1024)
    private String imageUrl6;

    /**
     * 빌더 패턴 생성자
     * 
     * @param scrapingQueue 연결된 스크래핑 큐
     * @param brand 브랜드명
     * @param name 상품명
     * @param gugusProductNo 구구스 상품번호
     * @param price 가격
     * @param imageUrl1~6 이미지 URL (최대 6개)
     */
    @Builder
    public Product(ScrapingQueue scrapingQueue, String brand, String name, String gugusProductNo, Long price,
                   String imageUrl1, String imageUrl2, String imageUrl3, String imageUrl4, String imageUrl5, String imageUrl6) {
        this.scrapingQueue = scrapingQueue;
        this.brand = brand;
        this.name = name;
        this.gugusProductNo = gugusProductNo;
        this.price = price;
        this.imageUrl1 = imageUrl1;
        this.imageUrl2 = imageUrl2;
        this.imageUrl3 = imageUrl3;
        this.imageUrl4 = imageUrl4;
        this.imageUrl5 = imageUrl5;
        this.imageUrl6 = imageUrl6;
    }
}
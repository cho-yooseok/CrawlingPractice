package com.example.gugusnewcrawling.service;

import com.example.gugusnewcrawling.config.Humanizer;
import com.example.gugusnewcrawling.entity.Product;
import com.example.gugusnewcrawling.entity.ScrapingQueue;
import com.example.gugusnewcrawling.entity.ScrapingStatus;
import com.example.gugusnewcrawling.repository.ProductRepository;
import com.example.gugusnewcrawling.repository.ScrapingQueueRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.io.IOException;
import java.net.URI;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.ThreadLocalRandom;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 크롤링 서비스 클래스
 * 
 * 구구스 쇼핑몰 크롤링의 핵심 비즈니스 로직을 담당합니다.
 * 
 * 주요 기능:
 * 1. URL 수집: 스크롤 기반으로 상품 URL 자동 수집
 * 2. HTML 다운로드: Selenium 또는 HTTP 병렬 방식으로 HTML 파일 다운로드
 * 3. HTML 파싱: 로컬 HTML 파일에서 상품 정보 추출 및 DB 저장
 * 4. 이미지 다운로드: 상품 이미지를 로컬에 다운로드
 * 5. 상태 관리: 작업 진행 상황 추적 및 상태 업데이트
 * 
 * 성능 최적화:
 * - 메모리 기반 URL 중복 체크 (HashSet)
 * - 배치 처리 (N+1 문제 방지)
 * - 병렬 처리 (HTTP 다운로드)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingService {

    /** Selenium WebDriver 인스턴스 (URL 수집 및 Selenium HTML 다운로드에 사용) */
    private final WebDriver driver;
    /** 스크래핑 대기열 레포지토리 */
    private final ScrapingQueueRepository scrapingQueueRepository;
    /** 상품 정보 레포지토리 */
    private final ProductRepository productRepository;

    /** 크롤링 대상 URL (구구스 가방 카테고리) */
    private static final String BASE_URL = "https://www.gugus.co.kr/goodsList/viewCategoryGoodsList?categoryNo=100&searchTerm=%EA%B0%80%EB%B0%A9";
    /** HTML 파일 저장 기본 경로 (300개 단위로 폴더 분리) */
    private static final String HTML_BASE_PATH = "C:\\Users\\code\\Documents\\project\\gugusnewcrawling\\gugusnewcrawling\\gugushtml";
    /** 이미지 파일 저장 기본 경로 (브랜드별 폴더 분리) */
    private static final String IMAGE_BASE_PATH = "C:\\Users\\code\\Documents\\project\\gugusnewcrawling\\gugusnewcrawling\\gugusimage";

    /** 기존 URL 메모리 캐시 (중복 체크 성능 향상, HashSet O(1) 조회) */
    private Set<String> existingUrls = new HashSet<>();

    /**
     * 기존 URL 목록을 DB에서 로드하여 메모리 캐시에 저장
     * 
     * 한 번만 로드하여 DB 쿼리를 최소화하고, HashSet으로 O(1) 중복 체크 성능을 보장합니다.
     * 빈 Set인 경우에만 로드하므로 중복 로드를 방지합니다.
     */
    private void loadExistingUrls() {
        if (existingUrls.isEmpty()) {
            log.info("DB에 저장된 기존 URL 목록을 메모리로 로드합니다...");
            existingUrls = scrapingQueueRepository.findAll().stream()
                                                    .map(ScrapingQueue::getUrl)
                                                    .collect(Collectors.toSet());
            log.info("총 {}개의 기존 URL을 메모리에 로드했습니다.", existingUrls.size());
        }
    }

    /**
     * 스크롤 기반 URL 수집 메서드
     * 
     * 구구스 쇼핑몰의 가방 카테고리 페이지를 스크롤하여 상품 URL을 자동으로 수집합니다.
     * 
     * 동작 방식:
     * 1. 기존 URL 목록을 메모리로 로드 (중복 체크용)
     * 2. 대상 페이지 접속
     * 3. 지정된 횟수만큼 스크롤하며 새로 로드된 상품 URL 수집
     * 4. 3회마다 자동 저장 (데이터 손실 방지)
     * 5. 자동 종료 조건:
     *    - 연속 300번 새 상품이 없으면 종료 (페이지 끝 도달)
     *    - 스크롤 높이가 300번 연속 같으면 종료 (더 이상 로드할 콘텐츠 없음)
     * 6. 오류 발생 시에도 계속 진행 (안정성)
     * 
     * 성능 최적화:
     * - 빠른 스크롤: 페이지 하단으로 즉시 이동
     * - 빠른 로드 감지: 20ms 간격으로 상품 로드 체크
     * - 배치 저장: 100개 단위로 DB 저장
     * 
     * @param maxScrollAttempts 최대 스크롤 횟수 (기본값: 5000, 최대: 50000)
     */
    @Transactional
    public void scrapeByScrolling(int maxScrollAttempts) {
        // 기존 URL 목록 로드 (중복 체크용 메모리 캐시)
        loadExistingUrls();
        driver.get(BASE_URL);
        log.info("대상 페이지에 접속하여 스크롤 기반 수집을 시작합니다. (최대 {}회 스크롤, 처음부터 시작)", maxScrollAttempts);
        Humanizer.randomSleep(500, 800); // 초기 대기 시간 단축
        JavascriptExecutor js = (JavascriptExecutor) driver;
        
        // 자동 종료를 위한 변수
        int noNewProductCount = 0; // 연속으로 새 상품이 없는 횟수
        final int MAX_NO_NEW_PRODUCT_COUNT = 300; // 연속 300번 새 상품이 없으면 종료 (약 46,000개 모두 크롤링을 위해 충분하게 설정)
        long previousScrollHeight = 0; // 이전 스크롤 높이
        int sameHeightCount = 0; // 스크롤 높이가 같은 횟수
        final int MAX_SAME_HEIGHT_COUNT = 300; // 스크롤 높이가 300번 연속 같으면 종료 (약 46,000개 모두 크롤링을 위해 충분하게 설정)
        
        for (int i = 0; i < maxScrollAttempts; i++) {
            // 변수 선언 (스코프 문제 해결)
            long currentScrollHeight = 0;
            long afterScrollHeight = 0;
            int currentProductCount = 0;
            int newProductCount = 0;
            
            try {
                List<WebElement> currentProducts = driver.findElements(By.cssSelector("#goods-ul > li"));
                currentProductCount = currentProducts.size();
                log.info("스크롤 시도 ({}/{}). 현재 수집된 상품 개수: {}개", i + 1, maxScrollAttempts, currentProductCount);
                
                // 현재 스크롤 높이 확인
                try {
                    currentScrollHeight = (Long) js.executeScript("return document.body.scrollHeight;");
                } catch (Exception e) {
                    log.warn("스크롤 높이 확인 중 오류 발생. 계속 진행합니다: {}", e.getMessage());
                }
                
                // 빠른 스크롤: 페이지 하단으로 즉시 이동
                try {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                } catch (Exception e) {
                    log.warn("스크롤 중 오류 발생. 계속 진행합니다: {}", e.getMessage());
                }
                
                // 상품 로드 감지: 최대 400ms까지 대기하되, 상품이 로드되면 즉시 진행 (최대한 빠르게)
                int waitCount = 0;
                int maxWaitCount = 20; // 최대 400ms 대기 (20 * 20ms) - 빠른 체크를 위해 간격 단축
                List<WebElement> newProducts = null;
                newProductCount = currentProductCount;
                
                // 최소 30ms 대기는 보장 (상품 로드 시간 확보, 최대한 빠르게)
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                while (waitCount < maxWaitCount) {
                    try {
                        Thread.sleep(20); // 20ms 간격으로 체크 (더 빠른 체크)
                        newProducts = driver.findElements(By.cssSelector("#goods-ul > li"));
                        newProductCount = newProducts.size();
                        
                        // 상품이 로드되면 즉시 종료
                        if (newProductCount > currentProductCount) {
                            break;
                        }
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.warn("상품 로드 확인 중 오류 발생. 계속 진행합니다: {}", e.getMessage());
                        break;
                    }
                }
                
                // 스크롤 높이 재확인
                try {
                    afterScrollHeight = (Long) js.executeScript("return document.body.scrollHeight;");
                } catch (Exception e) {
                    log.warn("스크롤 높이 재확인 중 오류 발생. 계속 진행합니다: {}", e.getMessage());
                    afterScrollHeight = currentScrollHeight;
                }
                
                if (newProductCount > currentProductCount) {
                    log.info("새로운 상품 {}개가 로드되었습니다. (이전: {}개 -> 현재: {}개)", newProductCount - currentProductCount, currentProductCount, newProductCount);
                    noNewProductCount = 0; // 새 상품이 있으면 카운터 리셋
                } else {
                    log.info("새로운 상품이 로드되지 않았습니다.");
                    noNewProductCount++; // 새 상품이 없으면 카운터 증가
                }
            } catch (Exception e) {
                log.error("스크롤 루프 중 오류 발생. 계속 진행합니다: {}", e.getMessage(), e);
                // 오류가 발생해도 계속 진행 (다음 반복에서 복구 시도)
                noNewProductCount++;
                afterScrollHeight = currentScrollHeight; // 오류 발생 시 기본값 설정
            }
            
            // 3회마다 URL 수집 및 저장 (더 자주 저장하여 오류 시 데이터 손실 방지)
            if ((i + 1) % 3 == 0) {
                try {
                    collectAndSaveUrls();
                } catch (Exception e) {
                    log.error("URL 수집 및 저장 중 오류 발생. 계속 진행합니다: {}", e.getMessage());
                    // 오류가 발생해도 계속 진행 (다음 저장 시도에서 복구)
                }
            }
            
            // 자동 종료 조건 1: 연속으로 300번 새 상품이 없으면 종료 (약 46,000개 모두 크롤링을 위해 충분하게 설정)
            if (noNewProductCount >= MAX_NO_NEW_PRODUCT_COUNT) {
                log.info("연속 {}번 새 상품이 로드되지 않아 스크롤을 종료합니다. (페이지 끝 도달 가능)", MAX_NO_NEW_PRODUCT_COUNT);
                break;
            }
            
            // 자동 종료 조건 2: 스크롤 높이가 연속으로 300번 같으면 종료 (약 46,000개 모두 크롤링을 위해 충분하게 설정)
            if (previousScrollHeight > 0 && currentScrollHeight == previousScrollHeight && afterScrollHeight == currentScrollHeight) {
                sameHeightCount++;
                if (sameHeightCount >= MAX_SAME_HEIGHT_COUNT) {
                    log.info("스크롤 높이가 연속 {}번 변하지 않아 스크롤을 종료합니다. (페이지 끝 도달 가능)", MAX_SAME_HEIGHT_COUNT);
                    break;
                }
            } else {
                sameHeightCount = 0; // 스크롤 높이가 변하면 카운터 리셋
            }
            
            previousScrollHeight = afterScrollHeight;
        }
        log.info("스크롤 완료. 최종 상품 URL을 수집합니다...");
        try {
            int finalCount = collectAndSaveUrls();
            log.info("총 {}개의 새로운 URL을 DB에 저장했습니다.", finalCount);
        } catch (Exception e) {
            log.error("최종 URL 수집 및 저장 중 오류 발생: {}", e.getMessage(), e);
            // 오류가 발생해도 로그는 출력
        }
    }

    /**
     * 현재 페이지에서 상품 URL을 수집하여 DB에 저장
     * 
     * 동작 방식:
     * 1. 페이지에서 모든 상품 링크 요소 찾기
     * 2. onclick 속성에서 상품번호 추출 (정규식 사용)
     * 3. 상품 URL 생성
     * 4. 메모리 캐시(existingUrls)를 사용한 중복 체크 (DB 쿼리 없이 O(1) 조회)
     * 5. 100개 단위로 배치 저장 (N+1 문제 방지)
     * 
     * @return 새로 추가된 URL 개수
     */
    private int collectAndSaveUrls() {
        // 페이지에서 모든 상품 링크 요소 찾기
        List<WebElement> productLinks = driver.findElements(By.cssSelector("#goods-ul > li > a.btn-link"));
        log.info("페이지에서 {}개의 상품 링크를 찾았습니다.", productLinks.size());
        // onclick 속성에서 상품번호 추출하기 위한 정규식 패턴: fn_prvwCheck('123456')
        Pattern pattern = Pattern.compile("fn_prvwCheck\\('(\\d+)'");
        int newUrlsCount = 0;
        int batchSize = 0;
        List<ScrapingQueue> batchList = new java.util.ArrayList<>();
        for (WebElement link : productLinks) {
            try {
                String onclickAttr = link.getAttribute("onclick");
                if (onclickAttr == null || onclickAttr.isEmpty()) continue;
                Matcher matcher = pattern.matcher(onclickAttr);
                if (matcher.find()) {
                    String goodsNo = matcher.group(1);
                    String productUrl = "https://www.gugus.co.kr/goods/viewGoods?goodsNo=" + goodsNo;
                    if (existingUrls.add(productUrl)) {
                        ScrapingQueue newItem = ScrapingQueue.builder().url(productUrl).status(ScrapingStatus.PENDING).build();
                        batchList.add(newItem);
                        newUrlsCount++;
                        batchSize++;
                        if (batchSize >= 100) {
                            scrapingQueueRepository.saveAll(batchList);
                            log.info("URL 100개 수집 및 저장 완료 (누적 신규: {}개)", newUrlsCount);
                            batchList.clear();
                            batchSize = 0;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("상품 링크 추출 중 오류 발생: {}", e.getMessage());
            }
        }
        if (!batchList.isEmpty()) {
            scrapingQueueRepository.saveAll(batchList);
        }
        return newUrlsCount;
    }
    
    /**
     * HTML 파일 배치 다운로드 (Selenium 방식)
     * 
     * PENDING 상태의 URL을 Selenium WebDriver로 HTML을 다운로드합니다.
     * 
     * 특징:
     * - 동적 콘텐츠 완벽 지원 (JavaScript 렌더링 대기)
     * - 느리지만 안정적 (약 1초/페이지)
     * - 봇 탐지 회피 기능 내장 (Humanizer 사용)
     * 
     * 파일 저장 규칙:
     * - 300개 단위로 폴더 분리 (파일 경로 길이 제한 회피)
     * - 폴더명: gugushtml00001_00300, gugushtml00301_00600, ...
     * - 파일명: {상품번호}.html
     * 
     * @param batchSize 한 번에 다운로드할 개수 (최대 100)
     * @return 작업 결과 메시지 (성공/실패 개수)
     */
    @Transactional
    public String downloadHtmlFilesBatch(int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<ScrapingQueue> pendingJobs = scrapingQueueRepository.findByStatus(ScrapingStatus.PENDING, pageable);
        if (pendingJobs.isEmpty()) {
            return "다운로드할 PENDING 상태의 작업이 없습니다.";
        }
        // 상태 변경 및 로그
        int successCount = 0;
        int failCount = 0;
        for (ScrapingQueue job : pendingJobs) {
            try {
                // HTML 저장 폴더 경로 동적 생성 (300개 단위로 폴더 분리)
                long queueId = job.getId();
                long folderGroup = (queueId - 1) / 300; // 300개 단위 그룹 계산
                long startId = folderGroup * 300 + 1;   // 폴더 시작 ID
                long endId = startId + 299;             // 폴더 끝 ID
                String folderName = String.format("gugushtml%05d_%05d", startId, endId);
                File targetDir = new File(HTML_BASE_PATH, folderName);
                if (!targetDir.exists()) targetDir.mkdirs();

                // Selenium으로 페이지 접속 및 렌더링 대기
                driver.get(job.getUrl());
                Humanizer.randomSleep(5000, 15000); // 봇 탐지 회피를 위한 랜덤 대기
                String pageSource = driver.getPageSource(); // 렌더링된 HTML 소스 가져오기
                
                // 파일명 생성: URL에서 상품번호 추출
                String goodsNo = job.getUrl().split("goodsNo=")[1];
                String fileName = goodsNo + ".html";
                File targetFile = new File(targetDir, fileName);
                FileUtils.writeStringToFile(targetFile, pageSource, StandardCharsets.UTF_8);
                job.setStatus(ScrapingStatus.COMPLETED);
                successCount++;
                log.info("[성공] HTML 저장 완료: {}", targetFile.getAbsolutePath());
            } catch (Exception e) {
                job.setStatus(ScrapingStatus.FAILED);
                failCount++;
                log.error("[실패] URL 처리 중 오류 발생: {}. 상태를 FAILED로 변경합니다.", job.getUrl(), e);
            }
        }
        scrapingQueueRepository.saveAll(pendingJobs);
        String resultMessage = String.format("HTML 다운로드 배치 완료. 성공: %d, 실패: %d", successCount, failCount);
        log.info(resultMessage);
        System.out.println("[CrawlingService] " + resultMessage);
        return resultMessage;
    }
    
    /**
     * HTML 파일 배치 파싱 메서드
     * 
     * COMPLETED 상태인 로컬 HTML 파일을 Jsoup으로 파싱하여 상품 정보를 추출하고 DB에 저장합니다.
     * 
     * 추출 정보:
     * - 브랜드명
     * - 상품명
     * - 구구스 상품번호
     * - 가격
     * - 이미지 URL (최대 6개)
     * 
     * 동작:
     * - 로컬 HTML 파일 읽기
     * - CSS Selector로 정보 추출
     * - 기존 상품이 있으면 업데이트 (upsert)
     * - 없으면 새로 생성
     * - 상태를 PARSED로 변경
     * 
     * @param batchSize 한 번에 파싱할 개수 (최대 1000)
     * @return 작업 결과 메시지 (성공/실패 개수)
     */
    @Transactional
    public String parseHtmlFilesBatch(int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<ScrapingQueue> jobsToParse = scrapingQueueRepository.findByStatus(ScrapingStatus.COMPLETED, pageable);
        if (jobsToParse.isEmpty()) {
            return "파싱할 대상(COMPLETED 상태)이 없습니다.";
        }
        int successCount = 0;
        int failCount = 0;
        for (ScrapingQueue job : jobsToParse) {
            // HTML 파일이 저장된 폴더 경로를 동적으로 계산
            long queueId = job.getId();
            long folderGroup = (queueId - 1) / 300;
            long startId = folderGroup * 300 + 1;
            long endId = startId + 299;
            String folderName = String.format("gugushtml%05d_%05d", startId, endId);
            String goodsNo = job.getUrl().split("goodsNo=")[1];
            File htmlFile = new File(new File(HTML_BASE_PATH, folderName), goodsNo + ".html");

            if (!htmlFile.exists()) {
                log.error("[파싱 실패] HTML 파일을 찾을 수 없습니다: {}", htmlFile.getAbsolutePath());
                job.setStatus(ScrapingStatus.FAILED);
                failCount++;
                continue;
            }
            try {
                // Jsoup으로 HTML 파일 파싱
                Document doc = Jsoup.parse(htmlFile, "UTF-8");

                // 1) 브랜드 추출 (CSS Selector 사용)
                String brandSelector = "#container > div.content-wrapper > div.prod-detail-header > div > div:nth-child(2) > div > div > div:nth-child(1) > div.info-head > div";
                String brand = textOrNull(doc.selectFirst(brandSelector));

                // 2) 상품이름 추출
                String nameSelector = "#container > div.content-wrapper > div.prod-detail-header > div > div:nth-child(2) > div > div > div:nth-child(1) > h1";
                String name = textOrNull(doc.selectFirst(nameSelector));

                // 3) 구구스상품번호 추출 (텍스트에서 "상품번호" 문자열 제거 후 숫자만 추출)
                String skuSelector = "#container > div.content-wrapper > div.prod-detail-header > div > div:nth-child(2) > div > div > div:nth-child(1) > div.bar-group > span:nth-child(1)";
                String gugusProductNoRaw = textOrNull(doc.selectFirst(skuSelector));
                String gugusProductNo = gugusProductNoRaw != null ? gugusProductNoRaw.replace("상품번호", "").trim() : null;
                if (gugusProductNo == null || gugusProductNo.isEmpty()) {
                    throw new IllegalStateException("상품번호를 찾을 수 없습니다: " + job.getUrl());
                }

                // 4) 가격 추출 (두 가지 셀렉터 중 하나 사용, 숫자만 추출)
                String priceSelector1 = "#container > div.content-wrapper > div.prod-detail-header > div > div:nth-child(2) > div > div > div:nth-child(1) > div.prod-price > span.goods-group.size-4xl > span.val";
                String priceSelector2 = "#container > div.content-wrapper > div.prod-detail-header > div > div:nth-child(2) > div > div > div:nth-child(1) > div.prod-price > span > span.val";
                String priceText = textOrNull(doc.selectFirst(priceSelector1));
                if (priceText == null || priceText.isEmpty()) {
                    priceText = textOrNull(doc.selectFirst(priceSelector2));
                }
                Long price = 0L;
                if (priceText != null && !priceText.isEmpty()) {
                    try {
                        // 숫자가 아닌 문자 제거 후 파싱
                        price = Long.parseLong(priceText.replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException ignored) {
                        price = 0L;
                    }
                }

                // 5) 이미지 URL 추출 (최대 6개, 없을 수 있음)
                String[] imageSelectors = new String[] {
                    "#gallery > div:nth-child(1) > img",
                    "#gallery > div:nth-child(2) > img",
                    "#gallery > div:nth-child(3) > img",
                    "#gallery > div:nth-child(4) > img",
                    "#gallery > div:nth-child(5) > img",
                    "#gallery > div:nth-child(6) > img"
                };
                List<String> imageUrls = new ArrayList<>();
                for (String sel : imageSelectors) {
                    Element img = doc.selectFirst(sel);
                    imageUrls.add(img != null ? img.attr("src") : null); // src 속성에서 이미지 URL 추출
                }

                // 업서트: queue 기준으로 기존 Product 있으면 갱신, 없으면 신규 생성
                Optional<Product> existing = productRepository.findByScrapingQueue(job);
                Product product;
                if (existing.isPresent()) {
                    product = existing.get();
                    product.setBrand(brand);
                    product.setName(name);
                    product.setGugusProductNo(gugusProductNo);
                    product.setPrice(price);
                    product.setImageUrl1(imageUrls.get(0));
                    product.setImageUrl2(imageUrls.get(1));
                    product.setImageUrl3(imageUrls.get(2));
                    product.setImageUrl4(imageUrls.get(3));
                    product.setImageUrl5(imageUrls.get(4));
                    product.setImageUrl6(imageUrls.get(5));
                } else {
                    product = Product.builder()
                            .scrapingQueue(job)
                            .brand(brand)
                            .name(name)
                            .gugusProductNo(gugusProductNo)
                            .price(price)
                            .imageUrl1(imageUrls.get(0))
                            .imageUrl2(imageUrls.get(1))
                            .imageUrl3(imageUrls.get(2))
                            .imageUrl4(imageUrls.get(3))
                            .imageUrl5(imageUrls.get(4))
                            .imageUrl6(imageUrls.get(5))
                            .build();
                }

                productRepository.save(product);
                job.setStatus(ScrapingStatus.PARSED);
                successCount++;
                log.info("[파싱 성공] 상품번호: {}, 브랜드: {}, 이름: {}, 가격: {}", gugusProductNo, brand, name, price);
            } catch (Exception e) {
                log.error("[파싱 실패] HTML 파싱 중 오류 발생: {}", job.getUrl(), e);
                job.setStatus(ScrapingStatus.FAILED);
                failCount++;
            }
        }
        scrapingQueueRepository.saveAll(jobsToParse);
        String result = String.format("HTML 파싱 배치 완료. 성공: %d, 실패: %d", successCount, failCount);
        log.info(result);
        System.out.println("[CrawlingService] " + result);
        return result;
    }

    /**
     * Jsoup Element에서 텍스트 추출 (null-safe)
     * 
     * @param el Jsoup Element (null 가능)
     * @return Element의 텍스트, null이면 null 반환
     */
    private String textOrNull(Element el) {
        return el == null ? null : el.text();
    }

    /**
     * 상품 이미지 배치 다운로드 메서드
     * 
     * PARSED 상태인 상품의 이미지를 인터넷에서 다운로드하여 로컬에 저장합니다.
     * 
     * 동작 방식:
     * 1. PARSED 상태의 작업 조회
     * 2. 각 상품의 최대 6개 이미지 URL 다운로드
     * 3. 브랜드별 폴더 구조로 저장 (gugusimage/{브랜드명}/)
     * 4. DB의 이미지 URL을 로컬 경로로 업데이트
     * 5. 상태를 IMAGE_DOWNLOADED로 변경 (최종 성공 상태)
     * 
     * 파일 저장 규칙:
     * - 브랜드명별 폴더 분리
     * - 파일명: {상품번호}_{순서}.{확장자}
     * 
     * @param batchSize 한 번에 처리할 상품 개수 (최대 1000)
     * @return 작업 결과 메시지 (성공/실패 개수)
     */
    @Transactional
    public String downloadImagesBatch(int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<ScrapingQueue> jobsToProcess = scrapingQueueRepository.findByStatus(ScrapingStatus.PARSED, pageable);
        if (jobsToProcess.isEmpty()) {
            return "이미지를 다운로드할 대상(PARSED 상태)이 없습니다.";
        }
        // 상태 변경 및 로그
        int successCount = 0;
        int failCount = 0;
        for (ScrapingQueue job : jobsToProcess) {
            try {
                Product product = productRepository.findByScrapingQueue(job)
                    .orElseThrow(() -> new IllegalStateException("Product 없음"));
                
                // 브랜드 기반 3단계 폴더 구조: BASE/브랜드명/ID범위
                String brandName = sanitizeBrandName(product.getBrand());
                long productId = product.getId();
                long folderGroup = (productId - 1) / 1800;
                long startId = folderGroup * 1800 + 1;
                long endId = startId + 1799;
                String folderName = String.format("gugusimage%05d_%05d", startId, endId);
                
                // BASE/브랜드명/ID범위 순서로 폴더 생성
                File brandDir = new File(IMAGE_BASE_PATH, brandName);
                File targetDir = new File(brandDir, folderName);
                if (!targetDir.exists()) targetDir.mkdirs();

                List<String> httpUrls = Arrays.asList(product.getImageUrl1(), product.getImageUrl2(), product.getImageUrl3(), product.getImageUrl4(), product.getImageUrl5(), product.getImageUrl6());
                List<String> localPaths = new ArrayList<>();
                boolean downloadSucceeded = true;
                for (int i = 0; i < httpUrls.size(); i++) {
                    String httpUrl = httpUrls.get(i);
                    if (httpUrl != null && httpUrl.startsWith("http")) {
                        try {
                            String fileExtension = getFileExtensionFromUrl(httpUrl);
                            String fileName = String.format("%s_%d.%s", product.getGugusProductNo(), i + 1, fileExtension);
                            File saveFile = new File(targetDir, fileName); // 수정된 경로에 저장
                            FileUtils.copyURLToFile(new URL(httpUrl), saveFile, 10000, 10000);
                            localPaths.add(saveFile.getAbsolutePath().replace("\\", "/"));
                            log.info("이미지 저장 성공: {}", saveFile.getAbsolutePath());
                        } catch (Exception e) {
                            log.error("이미지 다운로드 실패: {} - {}", httpUrl, e.getMessage());
                            localPaths.add("DOWNLOAD_FAILED: " + httpUrl);
                            downloadSucceeded = false;
                        }
                    } else {
                        localPaths.add(httpUrl);
                    }
                }
                product.setImageUrl1(localPaths.size() > 0 ? localPaths.get(0) : null);
                product.setImageUrl2(localPaths.size() > 1 ? localPaths.get(1) : null);
                product.setImageUrl3(localPaths.size() > 2 ? localPaths.get(2) : null);
                product.setImageUrl4(localPaths.size() > 3 ? localPaths.get(3) : null);
                product.setImageUrl5(localPaths.size() > 4 ? localPaths.get(4) : null);
                product.setImageUrl6(localPaths.size() > 5 ? localPaths.get(5) : null);
                productRepository.save(product);
                if (downloadSucceeded) {
                    job.setStatus(ScrapingStatus.IMAGE_DOWNLOADED);
                    successCount++;
                } else {
                    job.setStatus(ScrapingStatus.FAILED);
                    failCount++;
                }
            } catch (Exception e) {
                log.error("[이미지 다운로드 실패] 전체 프로세스 오류: {}", job.getUrl(), e);
                job.setStatus(ScrapingStatus.FAILED);
                failCount++;
            }
        }
        scrapingQueueRepository.saveAll(jobsToProcess);
        return String.format("이미지 다운로드 배치 완료. 성공: %d, 실패: %d", successCount, failCount);
    }

    /**
     * URL에서 파일 확장자 추출
     * 
     * URL의 파일명에서 확장자를 추출합니다.
     * 쿼리 파라미터가 있으면 제거하고, 확장자가 없으면 기본값 "jpg"를 반환합니다.
     * 
     * @param url 이미지 URL
     * @return 파일 확장자 (예: "jpg", "png", "jpeg")
     */
    private String getFileExtensionFromUrl(String url) {
        if (url == null || url.isEmpty()) return "jpg";
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        String fileNameWithoutQuery = fileName.split("\\?")[0]; // 쿼리 파라미터 제거
        int dotIndex = fileNameWithoutQuery.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileNameWithoutQuery.length() - 1) {
            return fileNameWithoutQuery.substring(dotIndex + 1);
        }
        return "jpg"; // 기본값
    }

    /**
     * 브랜드명을 파일 시스템 친화적인 이름으로 변환
     * 
     * 파일 시스템에 저장하기 위해 브랜드명을 정제합니다.
     * 
     * 변환 규칙:
     * - null/빈값 → "UNKNOWN"
     * - 특수문자 제거 (영문, 숫자, 한글, 공백만 허용)
     * - 공백은 언더스코어(_)로 변환
     * - 대문자 변환
     * - 최대 길이 50자 제한
     * 
     * @param brand 원본 브랜드명
     * @return 정제된 브랜드명 (파일/폴더명으로 사용 가능)
     */
    private String sanitizeBrandName(String brand) {
        if (brand == null || brand.trim().isEmpty()) {
            return "UNKNOWN";
        }
        // 특수문자 제거, 공백은 언더스코어로 변환
        String sanitized = brand.trim().toUpperCase()
                .replaceAll("[^A-Z0-9가-힣\\s]", "")
                .replaceAll("\\s+", "_");
        
        // 최대 50자로 제한
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        
        return sanitized.isEmpty() ? "UNKNOWN" : sanitized;
    }

    /**
     * 작업 현황 조회 메서드
     * 
     * 전체 스크래핑 작업의 상태별 개수를 조회하여 Map으로 반환합니다.
     * /status API에서 사용됩니다.
     * 
     * @return 상태별 개수를 담은 Map (키: 상태명, 값: 개수)
     */
    public Map<String, Long> getScrapingStatus() {
        Map<String, Long> statusMap = new HashMap<>();
        long pending = scrapingQueueRepository.countByStatus(ScrapingStatus.PENDING);
        long processing = scrapingQueueRepository.countByStatus(ScrapingStatus.PROCESSING);
        long completed = scrapingQueueRepository.countByStatus(ScrapingStatus.COMPLETED);
        long parsed = scrapingQueueRepository.countByStatus(ScrapingStatus.PARSED);
        long imageDownloaded = scrapingQueueRepository.countByStatus(ScrapingStatus.IMAGE_DOWNLOADED);
        long failed = scrapingQueueRepository.countByStatus(ScrapingStatus.FAILED);
        long total = scrapingQueueRepository.count();
        statusMap.put("TOTAL_IN_DB", total);
        statusMap.put("PENDING", pending);
        statusMap.put("PROCESSING", processing);
        statusMap.put("COMPLETED", completed);
        statusMap.put("PARSED", parsed);
        statusMap.put("IMAGE_DOWNLOADED", imageDownloaded);
        statusMap.put("FAILED", failed);
        return statusMap;
    }

    /**
     * 모든 데이터를 리셋하여 재다운로드 준비
     * 
     * 전체 크롤링 프로세스를 처음부터 다시 시작하고 싶을 때 사용합니다.
     * 
     * 동작:
     * 1. Product 테이블 전체 삭제
     * 2. ScrapingQueue 모든 항목을 PENDING 상태로 초기화
     * 3. 메모리 캐시(existingUrls) 초기화
     * 
     * 주의: 이 작업은 되돌릴 수 없습니다. 신중하게 사용하세요.
     * 
     * @return 리셋 완료 메시지 (초기화된 URL 개수 포함)
     */
    @Transactional
    public String resetAllData() {
        log.info("=== 데이터 리셋 시작 ===");
        
        // 1. Product 테이블 전체 삭제
        long productCount = productRepository.count();
        productRepository.deleteAll();
        log.info("Product 테이블 {}개 데이터 삭제 완료", productCount);
        
        // 2. ScrapingQueue 모든 항목을 PENDING으로 초기화
        long queueCount = scrapingQueueRepository.count();
        List<ScrapingQueue> allQueues = scrapingQueueRepository.findAll();
        for (ScrapingQueue queue : allQueues) {
            queue.setStatus(ScrapingStatus.PENDING);
        }
        scrapingQueueRepository.saveAll(allQueues);
        log.info("ScrapingQueue {}개 항목을 PENDING 상태로 초기화 완료", queueCount);
        
        // 3. 메모리의 existingUrls 캐시 초기화
        existingUrls.clear();
        log.info("메모리 캐시 초기화 완료");
        
        log.info("=== 데이터 리셋 완료 ===");
        return String.format("데이터 리셋 완료. URL: %d개를 PENDING 상태로 초기화했습니다.", queueCount);
    }

    // =========================
    // HTTP GET 병렬 HTML 다운로드
    // =========================

    @jakarta.annotation.Nullable
    @org.springframework.beans.factory.annotation.Value("${http.download.concurrent:8}")
    private Integer httpConcurrent;

    @org.springframework.beans.factory.annotation.Value("${http.download.timeout.ms:15000}")
    private int httpTimeoutMs;

    @org.springframework.beans.factory.annotation.Value("${http.download.retry.maxAttempts:3}")
    private int httpMaxAttempts;

    @org.springframework.beans.factory.annotation.Value("${http.download.jitter.min.ms:200}")
    private int httpJitterMinMs;

    @org.springframework.beans.factory.annotation.Value("${http.download.jitter.max.ms:1200}")
    private int httpJitterMaxMs;

    @org.springframework.beans.factory.annotation.Value("${http.download.userAgents:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36|Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15|Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36}")
    private String userAgentsConfig;

    @jakarta.annotation.Nullable
    @org.springframework.beans.factory.annotation.Value("${http.download.proxies:}")
    private String proxyListConfig; // 형식: host:port,host:port

    /**
     * 랜덤 User-Agent 선택
     * 
     * 설정된 User-Agent 목록에서 하나를 랜덤하게 선택하여 반환합니다.
     * 봇 탐지를 회피하기 위해 다양한 User-Agent를 사용합니다.
     * 
     * @return 선택된 User-Agent 문자열
     */
    private String pickRandomUserAgent() {
        String[] uas = userAgentsConfig.split("\\|");
        return uas[ThreadLocalRandom.current().nextInt(uas.length)].trim();
    }

    /**
     * 랜덤 지터(Jitter) 대기
     * 
     * 설정된 최소/최대 시간 사이에서 랜덤하게 대기합니다.
     * 너무 빠른 연속 요청을 방지하여 Rate Limiting을 회피합니다.
     * 
     * 설정값: http.download.jitter.min.ms ~ http.download.jitter.max.ms
     */
    private void randomJitterSleep() {
        int min = Math.max(0, httpJitterMinMs);
        int max = Math.max(min, httpJitterMaxMs);
        int sleep = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        try { Thread.sleep(sleep); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    /**
     * Proxy 설정이 있으면 ProxySelector 생성
     * 
     * application.properties의 http.download.proxies 설정값을 파싱하여
     * ProxySelector를 생성합니다.
     * 
     * 설정 형식: "host1:port1,host2:port2" (콤마 구분)
     * 
     * @return ProxySelector (설정이 없으면 Optional.empty())
     */
    private Optional<ProxySelector> buildProxySelectorIfAny() {
        if (proxyListConfig == null || proxyListConfig.isBlank()) return Optional.empty();
        String[] entries = proxyListConfig.split(",");
        List<Proxy> proxies = new ArrayList<>();
        for (String e : entries) {
            String s = e.trim();
            if (s.isEmpty()) continue;
            String[] hp = s.split(":");
            if (hp.length == 2) {
                try {
                    String host = hp[0];
                    int port = Integer.parseInt(hp[1]);
                    proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
                } catch (Exception ignored) {}
            }
        }
        if (proxies.isEmpty()) return Optional.empty();
        // 라운드로빈 ProxySelector (요청마다 순환하여 사용)
        return Optional.of(new ProxySelector() {
            private int idx = 0;
            @Override public List<Proxy> select(URI uri) {
                Proxy p = proxies.get((idx = (idx + 1) % proxies.size()));
                return Collections.singletonList(p);
            }
            @Override public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) { }
        });
    }

    /**
     * HTML 파일 배치 다운로드 (HTTP 병렬 방식) ⭐ 추천
     * 
     * PENDING 상태의 URL을 HTTP GET으로 병렬 다운로드합니다.
     * Selenium 방식보다 10배 이상 빠르므로 대량 다운로드 시 권장합니다.
     * 
     * 특징:
     * - Selenium 미사용 (빠름, 약 100ms/페이지)
     * - CompletableFuture + ExecutorService로 병렬 처리
     * - User-Agent 로테이션
     * - 지터 + 지수 백오프 재시도
     * - Proxy 지원 (설정 시)
     * 
     * 파일 저장 규칙:
     * - 300개 단위로 폴더 분리
     * - 폴더명: gugushtml00001_00300, ...
     * - 파일명: {상품번호}.html
     * 
     * @param batchSize 한 번에 다운로드할 개수 (최대 1000)
     * @return 작업 결과 메시지 (성공/실패 개수)
     */
    @Transactional
    public String downloadHtmlFilesBatchHttp(int batchSize) {
        // 동시성 레벨 결정 (설정값 또는 기본값 8)
        int concurrency = (httpConcurrent == null || httpConcurrent <= 0) ? 8 : httpConcurrent;

        Pageable pageable = PageRequest.of(0, batchSize);
        List<ScrapingQueue> pendingJobs = scrapingQueueRepository.findByStatus(ScrapingStatus.PENDING, pageable);
        if (pendingJobs.isEmpty()) {
            String message = "다운로드할 PENDING 상태의 작업이 없습니다.";
            log.info(message);
            return message;
        }

        // 작업 상태를 PROCESSING으로 변경
        for (ScrapingQueue job : pendingJobs) job.setStatus(ScrapingStatus.PROCESSING);
        scrapingQueueRepository.saveAll(pendingJobs);
        log.info("{}개의 작업을 PENDING에서 PROCESSING으로 변경하고 HTTP 병렬 다운로드를 시작합니다. (동시성: {})", pendingJobs.size(), concurrency);

        // HttpClient 생성 (타임아웃, Proxy 설정)
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(httpTimeoutMs));
        buildProxySelectorIfAny().ifPresent(clientBuilder::proxy); // Proxy 설정이 있으면 적용
        HttpClient httpClient = clientBuilder.build();

        // 스레드 풀 생성 (병렬 처리용)
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        final int[] successCount = {0}; // 성공 개수 (스레드 안전하게 접근하기 위해 배열 사용)
        final int[] failCount = {0};     // 실패 개수

        for (ScrapingQueue job : pendingJobs) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    // 폴더 결정 (큐 id 기준 300개 단위)
                    long queueId = job.getId();
                    long folderGroup = (queueId - 1) / 300;
                    long startId = folderGroup * 300 + 1;
                    long endId = startId + 299;
                    String folderName = String.format("gugushtml%05d_%05d", startId, endId);
                    File targetDir = new File(HTML_BASE_PATH, folderName);
                    if (!targetDir.exists()) targetDir.mkdirs();

                    String goodsNo = job.getUrl().split("goodsNo=")[1];
                    File targetFile = new File(targetDir, goodsNo + ".html");

                    // 재시도 로직: 최대 httpMaxAttempts번까지 시도
                    int attempt = 0;
                    while (true) {
                        attempt++;
                        try {
                            randomJitterSleep(); // Rate Limiting 회피를 위한 랜덤 대기
                            String ua = pickRandomUserAgent(); // 랜덤 User-Agent 선택
                            // HTTP 요청 생성
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create(job.getUrl()))
                                    .timeout(Duration.ofMillis(httpTimeoutMs))
                                    .header("User-Agent", ua)
                                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                                    .GET()
                                    .build();
                            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                            if (res.statusCode() == 200) {
                                // 성공: HTML 파일 저장
                                FileUtils.writeStringToFile(targetFile, res.body(), StandardCharsets.UTF_8);
                                job.setStatus(ScrapingStatus.COMPLETED);
                                synchronized (successCount) { successCount[0]++; } // 스레드 안전한 카운터 증가
                                log.info("[성공][HTTP] HTML 저장 완료: {}", targetFile.getAbsolutePath());
                                break; // 성공 시 루프 종료
                            } else {
                                // HTTP 상태코드가 200이 아닌 경우
                                if (attempt >= httpMaxAttempts) {
                                    job.setStatus(ScrapingStatus.FAILED);
                                    synchronized (failCount) { failCount[0]++; }
                                    log.warn("[실패][HTTP] 상태코드 {} - {} (최대 재시도 초과)", res.statusCode(), job.getUrl());
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // 예외 발생 시 재시도
                            if (attempt >= httpMaxAttempts) {
                                job.setStatus(ScrapingStatus.FAILED);
                                synchronized (failCount) { failCount[0]++; }
                                log.error("[실패][HTTP] 예외 - {} (최대 재시도 초과)", job.getUrl(), e);
                                break;
                            }
                        }
                        // 지수 백오프 + 지터 (재시도 전 대기 시간)
                        // 시도 횟수가 많을수록 더 오래 대기 (2^attempt * 250ms, 최대 5초)
                        long backoff = (long) Math.min(5000, Math.pow(2, attempt) * 250);
                        try { Thread.sleep(backoff + ThreadLocalRandom.current().nextInt(250)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                } catch (Exception e) {
                    job.setStatus(ScrapingStatus.FAILED);
                    synchronized (failCount) { failCount[0]++; }
                    log.error("[실패][HTTP] 전체 처리 예외: {}", job.getUrl(), e);
                }
            }, pool));
        }

        // 모든 비동기 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 상태 업데이트 저장 (COMPLETED 또는 FAILED)
        scrapingQueueRepository.saveAll(pendingJobs);

        // 스레드 풀 종료
        pool.shutdown();
        try { pool.awaitTermination(1, TimeUnit.MINUTES); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        String resultMessage = String.format("[HTTP] HTML 다운로드 배치 완료. 성공: %d, 실패: %d", successCount[0], failCount[0]);
        log.info(resultMessage);
        System.out.println("[CrawlingService] " + resultMessage);
        return resultMessage;
    }
}
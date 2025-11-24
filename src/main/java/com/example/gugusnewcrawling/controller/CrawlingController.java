package com.example.gugusnewcrawling.controller;

import com.example.gugusnewcrawling.service.CrawlingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 크롤링 제어 REST API 컨트롤러
 * 
 * 구구스 상품 크롤링 시스템의 모든 작업을 제어하는 REST API 엔드포인트를 제공합니다.
 * 
 * 주요 기능:
 * 1. URL 수집 (스크롤 기반)
 * 2. HTML 다운로드 (Selenium / HTTP 병렬)
 * 3. HTML 파싱
 * 4. 이미지 다운로드
 * 5. 작업 현황 조회
 * 6. 데이터 리셋
 * 
 * 모든 작업은 비동기로 실행되며, 즉시 응답을 반환합니다.
 * 진행 상황은 /status API를 통해 확인할 수 있습니다.
 */
@Tag(name = "Crawling Controller", description = "구구스 상품 스크래핑 제어 API")
@RestController
@RequiredArgsConstructor
public class CrawlingController {

    private final CrawlingService crawlingService;

    /**
     * 스크롤 기반 URL 수집 API
     * 
     * 구구스 쇼핑몰의 가방 카테고리 페이지를 스크롤하여 상품 URL을 자동으로 수집합니다.
     * 
     * 동작 방식:
     * - Selenium으로 페이지 접속
     * - 지정된 횟수만큼 스크롤하며 새로 로드된 상품 URL 수집
     * - 3회마다 자동 저장 (데이터 손실 방지)
     * - 연속 300번 새 상품이 없거나 스크롤 높이가 300번 연속 같으면 자동 종료
     * - 오류 발생 시에도 계속 진행
     * 
     * @param scrollCount 최대 스크롤 횟수 (기본값: 5000, 최대: 50000)
     * @return 작업 시작 확인 메시지
     */
    @Operation(summary = "1. 스크롤 기반 URL 수집 (배치)",
               description = "지정된 횟수(scrollCount)만큼 페이지 스크롤을 실행하여 새로 로드된 상품 URL을 수집합니다. " +
                       "처음부터 시작하여 스크롤을 충분히 내려 다수의 상품을 모두 크롤링합니다. " +
                       "연속으로 300번 새 상품이 없거나 스크롤 높이가 300번 연속 같으면 자동으로 종료됩니다. " +
                       "오류 발생 시에도 계속 진행하며, 3회마다 자동 저장합니다. " +
                       "스크롤 속도는 인간이 할 수 있는 최대한 빠르게 설정되었습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "스크롤 기반 URL 수집 작업이 성공적으로 시작됨"),
        @ApiResponse(responseCode = "400", description = "파라미터 'scrollCount'가 유효한 범위(1~50000)를 벗어남")
    })
    @PostMapping("/scrape-by-scrolling")
    public ResponseEntity<String> scrapeByScrolling(
        @Parameter(description = "최대 스크롤 횟수 (기본값: 5000, 최대: 50000). 자동 종료 조건이 충족되면 더 일찍 종료됩니다.", example = "5000")
        @RequestParam(defaultValue = "5000") int scrollCount) {
        if (scrollCount <= 0 || scrollCount > 50000) {
            return ResponseEntity.badRequest().body("스크롤 횟수는 1에서 50000 사이여야 합니다.");
        }
        // 비동기로 실행 (별도 스레드에서 작업 수행)
        new Thread(() -> crawlingService.scrapeByScrolling(scrollCount)).start();
        return ResponseEntity.ok(scrollCount + "회 스크롤 기반의 URL 수집을 시작했습니다. (처음부터 시작, 자동 종료 조건 충족 시 조기 종료, 오류 발생 시에도 계속 진행)");
    }

    /**
     * HTML 파일 배치 다운로드 (HTTP 병렬 방식)
     * 
     * PENDING 상태의 URL을 HTTP GET으로 병렬 다운로드합니다.
     * 
     * 특징:
     * - Selenium 미사용 (빠름, 약 100ms/페이지)
     * - 8개 스레드로 병렬 처리 (기본값, 설정 가능)
     * - User-Agent 로테이션
     * - 지터 + 지수 백오프 재시도
     * - Proxy 지원 (설정 시)
     * 
     * Selenium 방식보다 10배 이상 빠르므로 대량 다운로드 시 권장합니다.
     * 
     * @param size 한 번에 다운로드할 개수 (기본값: 10, 최대: 1000)
     * @return 작업 시작 확인 메시지
     */
    @Operation(summary = "2. HTML 파일 배치 다운로드 (HTTP 병렬, 셀레니움 미사용, GET으로 병렬다운로드드)",
               description = "'PENDING' 상태의 URL을 지정된 개수(size)만큼 가져와 HTTP GET으로 병렬 다운로드합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "HTTP 다운로드 작업이 성공적으로 시작됨"),
        @ApiResponse(responseCode = "400", description = "파라미터 'size'가 유효한 범위(1~1000)를 벗어남")
    })
    @PostMapping("/download-html-batch-http")
    public ResponseEntity<String> downloadHtmlBatchHttp(
        @Parameter(description = "한 번에 다운로드할 개수 (기본값: 10, 최대: 1000)", example = "50")
        @RequestParam(defaultValue = "10") int size) {
        if (size <= 0 || size > 1000) {
            return ResponseEntity.badRequest().body("배치 사이즈는 1에서 1000 사이여야 합니다.");
        }
        // 비동기로 실행
        new Thread(() -> crawlingService.downloadHtmlFilesBatchHttp(size)).start();
        return ResponseEntity.ok(size + "개 단위의 HTTP HTML 다운로드 배치를 시작했습니다.");
    }

    /**
     * HTML 파일 파싱 API
     * 
     * COMPLETED 상태인 로컬 HTML 파일을 파싱하여 상품 정보를 추출하고 DB에 저장합니다.
     * 
     * 추출 정보:
     * - 브랜드명
     * - 상품명
     * - 구구스 상품번호
     * - 가격
     * - 이미지 URL (최대 6개)
     * 
     * 동작:
     * - 기존 상품이 있으면 업데이트 (upsert)
     * - 없으면 새로 생성
     * - 상태를 PARSED로 변경
     * 
     * @param size 한 번에 파싱할 개수 (기본값: 100, 최대: 1000)
     * @return 작업 시작 확인 메시지
     */
    @Operation(summary = "3. 로컬 HTML 파일 파싱 (배치)",
               description = "'COMPLETED'(다운로드 완료) 상태인 HTML 파일을 지정된 개수(size)만큼 파싱하여 상품 정보를 DB에 저장하고 'PARSED' 상태로 변경합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "HTML 파싱 작업이 성공적으로 시작됨"),
        @ApiResponse(responseCode = "400", description = "파라미터 'size'가 유효한 범위(1~1000)를 벗어남")
    })
    @PostMapping("/parse-html-batch")
    public ResponseEntity<String> parseHtmlBatch(
        @Parameter(description = "한 번에 파싱할 개수 (기본값: 100, 최대: 1000)", example = "100")
        @RequestParam(defaultValue = "100") int size) {
        if (size <= 0 || size > 1000) {
            return ResponseEntity.badRequest().body("배치 사이즈는 1에서 1000 사이여야 합니다.");
        }
        // 비동기로 실행
        new Thread(() -> crawlingService.parseHtmlFilesBatch(size)).start();
        return ResponseEntity.ok(size + "개 단위의 HTML 파싱 배치를 시작했습니다.");
    }

    /**
     * 상품 이미지 다운로드 API
     * 
     * PARSED 상태인 상품의 이미지를 인터넷에서 다운로드하여 로컬에 저장합니다.
     * 
     * 동작:
     * - 최대 6개의 이미지 URL을 다운로드
     * - 브랜드별 폴더 구조로 저장 (gugusimage/{브랜드명}/)
     * - DB의 이미지 URL을 로컬 경로로 업데이트
     * - 상태를 IMAGE_DOWNLOADED로 변경 (최종 성공 상태)
     * 
     * @param size 한 번에 처리할 상품 개수 (기본값: 10, 최대: 1000)
     * @return 작업 시작 확인 메시지
     */
    @Operation(summary = "4. 상품 이미지 다운로드 (배치)",
               description = "'PARSED' 상태인 상품의 인터넷 이미지 URL을 로컬에 다운로드하고, DB의 경로를 로컬 경로로 업데이트하며 'IMAGE_DOWNLOADED' 상태로 변경합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이미지 다운로드 작업이 성공적으로 시작됨"),
        @ApiResponse(responseCode = "400", description = "파라미터 'size'가 유효한 범위(1~1000)를 벗어남")
    })
    @PostMapping("/download-images-batch")
    public ResponseEntity<String> downloadImagesBatch(
        @Parameter(description = "한 번에 처리할 상품(이미지) 개수 (기본값: 10, 최대: 1000)", example = "10")
        @RequestParam(defaultValue = "10") int size) {
        if (size <= 0 || size > 1000) {
            return ResponseEntity.badRequest().body("배치 사이즈는 1에서 1000 사이여야 합니다.");
        }
        // 비동기로 실행
        new Thread(() -> crawlingService.downloadImagesBatch(size)).start();
        return ResponseEntity.ok(size + "개 단위의 이미지 다운로드 배치를 시작했습니다.");
    }

    /**
     * 작업 현황 조회 API
     * 
     * 전체 스크래핑 작업의 상태별 개수를 요약하여 반환합니다.
     * 
     * 반환 정보:
     * - TOTAL_IN_DB: 전체 레코드 수
     * - PENDING: HTML 다운로드 대기
     * - PROCESSING: 처리 중
     * - COMPLETED: HTML 다운로드 완료, 파싱 대기
     * - PARSED: 파싱 완료, 이미지 다운로드 대기
     * - IMAGE_DOWNLOADED: 이미지 다운로드 완료 (최종 성공)
     * - FAILED: 실패
     * 
     * @return 상태별 개수를 담은 Map
     */
    @Operation(summary = "5. 전체 작업 현황 조회",
               description = "전체 스크래핑 큐의 상태별 개수를 요약하여 보여줍니다.")
    @ApiResponse(responseCode = "200", description = "현재 작업 현황",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "TOTAL_IN_DB": 1500,
                  "PENDING": 1000,
                  "PROCESSING": 0,
                  "COMPLETED": 400,
                  "PARSED": 50,
                  "IMAGE_DOWNLOADED": 45,
                  "FAILED": 5
                }
                """)))
    @GetMapping("/status")
    public ResponseEntity<Map<String, Long>> getStatus() {
        Map<String, Long> status = crawlingService.getScrapingStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 데이터 리셋 API
     * 
     * 모든 상품 데이터를 삭제하고 URL 상태를 PENDING으로 초기화합니다.
     * 
     * 동작:
     * - Product 테이블 전체 삭제
     * - ScrapingQueue 모든 항목을 PENDING 상태로 초기화
     * - 메모리 캐시 초기화
     * 
     * 용도:
     * - HTTP/이미지 재다운로드 필요 시
     * - 전체 프로세스 재시작 시
     * 
     * 주의: 이 작업은 되돌릴 수 없습니다. 신중하게 사용하세요.
     * 
     * @return 리셋 완료 메시지
     */
    @Operation(summary = "6. 데이터 리셋 (재다운로드용)",
               description = "모든 상품 데이터를 삭제하고 URL 상태를 PENDING으로 초기화합니다. HTTP/이미지 재다운로드를 위한 리셋 기능입니다.")
    @ApiResponse(responseCode = "200", description = "데이터 리셋 완료")
    @PostMapping("/reset")
    public ResponseEntity<String> resetData() {
        String result = crawlingService.resetAllData();
        return ResponseEntity.ok(result);
    }
}
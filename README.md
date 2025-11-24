#  크롤링 시스템 ( Crawling System)

> **개인 크롤링 학습용 프로젝트**  
> 이 프로젝트는 웹 크롤링, 데이터 수집, Spring Boot, Selenium 등의 기술을 학습하기 위해 개인적으로 개발한 프로젝트입니다.  


---

##  목차

- [프로젝트 개요](#프로젝트-개요)
- [시스템 아키텍처](#시스템-아키텍처)
- [기술 스택](#기술-스택)
- [주요 기능](#주요-기능)
- [API 명세서](#api-명세서)
- [설치 및 실행](#설치-및-실행)
- [프로젝트 구조](#프로젝트-구조)
- [학습 내용](#학습-내용)
- [주의사항](#주의사항)

---

##  프로젝트 개요

 온라인 쇼핑몰의 가방 카테고리 상품 정보를 자동으로 수집하고 관리하는 크롤링 시스템입니다.

### 주요 특징

- **다단계 스크래핑 파이프라인**: URL 수집 → HTML 다운로드 → 정보 파싱 → 이미지 다운로드
- **상태 기반 작업 관리**: PENDING → PROCESSING → COMPLETED → PARSED → IMAGE_DOWNLOADED
- **병렬 처리 지원**: HTTP 다운로드 시 멀티스레딩으로 성능 최적화
- **반복 크롤링 방지**: 메모리 기반 URL 중복 체크
- **Humanizer 패턴**: 자동화 탐지 회피를 위한 인간형 행동 시뮬레이션
- **배치 처리**: 대량 데이터 효율적 처리

### 학습 목적

이 프로젝트를 통해 다음 기술들을 학습했습니다:

- Spring Boot 애플리케이션 개발
- Selenium WebDriver를 활용한 동적 웹 크롤링
- HTTP Client를 통한 병렬 데이터 수집
- Jsoup을 이용한 HTML 파싱
- Spring Data JPA를 활용한 데이터 관리
- 비동기 처리 및 멀티스레딩
- REST API 설계 및 구현
- Swagger를 통한 API 문서화

---

##  시스템 아키텍처

### 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Application                      │
│                                                                   │
│  ┌──────────────┐         ┌──────────────┐                      │
│  │  Controller  │────────▶│   Service    │                      │
│  │ (REST API)   │         │ (Business)   │                      │
│  └──────────────┘         └──────┬───────┘                      │
│                                  │                                │
│  ┌──────────────────────────────┼──────────────────────────────┐│
│  │                              │                              ││
│  │      ┌───────────────────────▼────────┐                     ││
│  │      │    ScrapingQueue (MySQL)      │                     ││
│  │      │  - URL                         │                     ││
│  │      │  - Status (PENDING, ...)       │                     ││
│  │      │  - Created/Updated Time        │                     ││
│  │      └────────────────────────────────┘                     ││
│  │                              │                                ││
│  │      ┌───────────────────────▼────────┐                     ││
│  │      │        Product (MySQL)         │                     ││
│  │      │  - Brand, Name                 │                     ││
│  │      │  - Gugus Product No            │                     ││
│  │      │  - Price                       │                     ││
│  │      │  - Image URLs (1~6)            │                     ││
│  │      └────────────────────────────────┘                     ││
│  └──────────────────────────────────────────────────────────────┘│
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    External Components                        ││
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐           ││
│  │  │  Selenium  │  │  HttpClient│  │   Jsoup    │           ││
│  │  │  WebDriver │  │   (Java)   │  │  Parser    │           ││
│  │  └────────────┘  └────────────┘  └────────────┘           ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                  File System Storage                         ││
│  │  ┌─────────────────┐      ┌─────────────────┐             ││
│  │  │  gugushtml/     │      │  gugusimage/    │             ││
│  │  │  (300개 단위)    │      │  (브랜드별)     │             ││
│  │  │  - *.html       │      │  - *.jpg/png    │             ││
│  │  └─────────────────┘      └─────────────────┘             ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 데이터 흐름

```
1. URL 수집 (Selenium)
   └─> ScrapingQueue 테이블에 PENDING 상태로 저장

2. HTML 다운로드 (HTTP 병렬)
   └─> 로컬 파일 저장 (gugushtml/) + 상태를 COMPLETED로 변경

3. HTML 파싱 (Jsoup)
   └─> Product 테이블에 저장 + 상태를 PARSED로 변경

4. 이미지 다운로드 (HttpClient)
   └─> 로컬 파일 저장 (gugusimage/{브랜드}/) + 상태를 IMAGE_DOWNLOADED로 변경
```

### 상태 관리 워크플로우

```
PENDING → PROCESSING → COMPLETED → PARSED → IMAGE_DOWNLOADED
                           ↓           ↓
                        FAILED      FAILED
```

각 단계별 상태:
- **PENDING**: URL 수집 완료, HTML 다운로드 대기
- **PROCESSING**: 작업 처리 중 (HTML 다운로드, 파싱, 이미지 다운로드 등)
- **COMPLETED**: HTML 다운로드 완료, 파싱 대기
- **PARSED**: HTML 파싱 및 정보 저장 완료, 이미지 다운로드 대기
- **IMAGE_DOWNLOADED**: 이미지 다운로드 완료 (최종 성공 상태)
- **FAILED**: 처리 중 오류 발생

---

##  기술 스택

### Backend
- **Spring Boot 3.2.5** - 웹 애플리케이션 프레임워크
- **Java 17** - 프로그래밍 언어
- **Spring Data JPA** - 데이터베이스 접근 계층
- **MySQL 8.0** - 관계형 데이터베이스

### 크롤링/파싱
- **Selenium WebDriver 4.21.0** - 동적 웹 크롤링
- **Jsoup 1.17.2** - HTML 파싱
- **Java HttpClient** - HTTP 요청 처리
- **WebDriverManager 5.9.1** - ChromeDriver 자동 관리

### 기타
- **Lombok** - 보일러플레이트 코드 제거
- **Commons IO 2.11.0** - 파일 처리
- **Swagger (SpringDoc) 2.5.0** - API 문서화
- **Spring Actuator** - 모니터링

### 빌드 도구
- **Gradle 8.x**

---

##  주요 기능

### 1. 스크롤 기반 URL 수집
- 무한 스크롤 페이지 자동 탐색
- 최대 50,000회 스크롤 지원
- 메모리 기반 중복 체크 (HashSet)
- 100개 단위 배치 저장
- 자동 종료 조건: 연속 300번 새 상품 없음 또는 스크롤 높이 불변

### 2. HTML 다운로드 (HTTP 병렬 방식)
- CompletableFuture + ExecutorService
- 기본 동시성 8 (설정 가능)
- User-Agent 로테이션
- 지터(Jitter) + 지수 백오프 재시도
- Proxy 지원 (설정 시)

### 3. HTML 파싱
- Jsoup으로 CSS Selector 기반 추출
- 브랜드, 이름, 상품번호, 가격
- 최대 6개 이미지 URL
- Upsert 방식 (기존 데이터 업데이트)

### 4. 이미지 다운로드
- Commons IO 사용
- 파일 확장자 자동 감지
- 브랜드별 폴더 구조
- 실패 시 FAILED 상태 표시

### 5. Humanizer 패턴
- 랜덤 대기 시간 (50~150ms)
- 점진적 스크롤 (150~250px 단계)
- 부드러운 마우스 움직임
- 랜덤 화면 크기 조정

---

##  API 명세서

### Swagger UI 접속
```
http://localhost:8080/swagger-ui.html
```

### 1. 스크롤 기반 URL 수집

**엔드포인트:** `POST /scrape-by-scrolling`

**설명:** 페이지를 스크롤하여 상품 URL을 자동 수집합니다.

**파라미터:**
- `scrollCount` (int, 기본값: 5000, 최대: 50000): 최대 스크롤 횟수

**응답 예시:**
```json
"5000회 스크롤 기반의 URL 수집을 시작했습니다. (처음부터 시작, 자동 종료 조건 충족 시 조기 종료, 오류 발생 시에도 계속 진행)"
```

**동작:**
- Selenium으로 페이지 접속
- 지정된 횟수만큼 스크롤하며 새로 로드된 상품 URL 수집
- 3회마다 자동 저장
- 연속 300번 새 상품이 없거나 스크롤 높이가 300번 연속 같으면 자동 종료

---

### 2. HTML 파일 배치 다운로드 (HTTP 병렬)

**엔드포인트:** `POST /download-html-batch-http`

**설명:** PENDING 상태 URL을 HTTP GET으로 병렬 다운로드합니다.

**파라미터:**
- `size` (int, 기본값: 10, 최대: 1000): 배치 크기

**응답 예시:**
```json
"[HTTP] HTML 다운로드 배치 완료. 성공: 998, 실패: 2"
```

**특징:**
- Selenium 미사용 (빠름, 약 100ms/페이지)
- 8개 스레드로 병렬 처리
- User-Agent 로테이션
- 지터 + 지수 백오프 재시도

**설정 (application.properties):**
```properties
http.download.concurrent=8           # 동시성 레벨
http.download.timeout.ms=15000       # 타임아웃
http.download.retry.maxAttempts=3    # 최대 재시도
http.download.jitter.min.ms=200      # 지터 최소값
http.download.jitter.max.ms=1200     # 지터 최대값
http.download.userAgents=...         # User-Agent 목록 (| 구분자)
http.download.proxies=               # Proxy 목록 (콤마 구분)
```

---

### 3. HTML 파일 파싱

**엔드포인트:** `POST /parse-html-batch`

**설명:** COMPLETED 상태 HTML 파일에서 상품 정보를 추출합니다.

**파라미터:**
- `size` (int, 기본값: 100, 최대: 1000): 배치 크기

**응답 예시:**
```json
"HTML 파싱 배치 완료. 성공: 998, 실패: 2"
```

**추출 정보:**
- 브랜드명
- 상품명
- 구구스 상품번호
- 가격
- 이미지 URL (최대 6개)

---

### 4. 상품 이미지 다운로드

**엔드포인트:** `POST /download-images-batch`

**설명:** PARSED 상태 상품의 이미지를 다운로드합니다.

**파라미터:**
- `size` (int, 기본값: 10, 최대: 1000): 배치 크기

**응답 예시:**
```json
"이미지 다운로드 배치 완료. 성공: 95, 실패: 5"
```

**저장 위치:** `gugusimage/{브랜드명}/` 폴더

---

### 5. 작업 현황 조회

**엔드포인트:** `GET /status`

**설명:** 전체 스크래핑 작업의 상태별 개수를 조회합니다.

**응답 예시:**
```json
{
  "TOTAL_IN_DB": 1500,
  "PENDING": 800,
  "PROCESSING": 0,
  "COMPLETED": 500,
  "PARSED": 180,
  "IMAGE_DOWNLOADED": 10,
  "FAILED": 10
}
```

---

### 6. 데이터 리셋

**엔드포인트:** `POST /reset`

**설명:** 모든 상품 데이터를 삭제하고 URL 상태를 PENDING으로 초기화합니다.

**응답 예시:**
```json
"데이터 리셋 완료. URL: 1500개를 PENDING 상태로 초기화했습니다."
```

**용도:** HTTP/이미지 재다운로드 시 사용

---

##  설치 및 실행

### 사전 요구사항
- **Java 17 이상**
- **MySQL 8.0 이상**
- **Chrome/Chromium 브라우저** (Selenium용)
- **Gradle 8.x** (프로젝트에 포함됨)

### 1. 데이터베이스 설정

```sql
CREATE DATABASE gugus_newdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. application.properties 설정

```properties
# 데이터베이스 연결
spring.datasource.url=jdbc:mysql://localhost:3306/gugus_newdb?serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=your_password

# JPA 설정
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Selenium 설정
selenium.use.remote=false
selenium.hub.url=http://localhost:4444/wd/hub

# HTTP 병렬 다운로드 설정
http.download.concurrent=8
http.download.timeout.ms=15000
http.download.retry.maxAttempts=3
http.download.jitter.min.ms=200
http.download.jitter.max.ms=1200
```

### 3. 빌드 및 실행

**Windows:**
```powershell
.\gradlew.bat build
.\gradlew.bat bootRun
```

**Linux/Mac:**
```bash
./gradlew build
./gradlew bootRun
```

### 4. 디렉토리 자동 생성

프로젝트 실행 시 다음 디렉토리가 자동 생성됩니다:
- `gugushtml/` - HTML 파일 저장 (300개 단위 폴더)
- `gugusimage/` - 이미지 파일 저장 (브랜드별 폴더)

---

##  프로젝트 구조

```
src/main/java/com/example/gugusnewcrawling/
├── config/
│   ├── Humanizer.java          # 봇 탐지 회피 유틸리티
│   ├── SeleniumConfig.java     # Selenium 설정
│   └── SwaggerConfig.java      # Swagger 설정
├── controller/
│   └── CrawlingController.java # REST API 엔드포인트
├── entity/
│   ├── Product.java            # 상품 엔티티
│   ├── ScrapingQueue.java      # 스크래핑 큐 엔티티
│   └── ScrapingStatus.java     # 상태 열거형
├── repository/
│   ├── ProductRepository.java
│   └── ScrapingQueueRepository.java
├── service/
│   ├── CrawlingService.java    # 핵심 비즈니스 로직
│   └── ScrapingProgressTracker.java
└── GugusnewcrawlingApplication.java
```

---

##  학습 내용

이 프로젝트를 통해 학습한 주요 내용들입니다.

### 1. Selenium vs HTTP Client 비교

**Selenium**
- 장점: JavaScript 렌더링 완벽 지원
- 단점: 메모리/CPU 부하, 느림 (1초/페이지)
- 용도: 동적 콘텐츠 크롤링

**Java HttpClient**
- 장점: 빠름 (100ms/페이지), 경량
- 단점: 정적 페이지만 가능
- 용도: 대량 배치 다운로드

**개선:** URL 수집은 Selenium, HTML 다운로드는 HttpClient로 분리하여 속도 10배 향상

### 2. CompletableFuture와 ExecutorService

비동기 병렬 처리로 처리량을 크게 향상시켰습니다:
- ExecutorService로 스레드 풀 관리
- CompletableFuture로 비동기 작업 조합
- 동시성 레벨 조절로 안정성 확보

### 3. Humanizer 패턴

봇 탐지를 회피하기 위한 인간 행동 시뮬레이션:
- 랜덤 대기 시간
- 점진적 스크롤
- 부드러운 마우스 움직임
- 랜덤 화면 크기

### 4. 상태 기반 워크플로우

각 단계별 명확한 상태 정의로:
- 실패 지점 추적 가능
- 재시작 시 실패된 작업만 재처리
- 작업 진행 상황 모니터링

### 5. 메모리 효율성

- HashSet 기반 URL 중복 체크 (O(1) 조회)
- 배치 저장으로 N+1 문제 방지
- 메모리 캐시 활용으로 DB 쿼리 최소화

### 6. 지터(Jitter)와 백오프

- 서버 부하 분산
- Rate limiting 회피
- 재시도 시 천천히 부하 증가

---

##  주의사항

**과도한 요청 방지**: Rate limiting을 준수하여 서버에 부하를 주지 마세요.



---

##  라이선스

이 프로젝트는 **개인 학습 목적**으로 작성되었습니다.  
상업적 용도로 사용하지 마세요.

---

##  참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Selenium 공식 문서](https://www.selenium.dev/documentation/)
- [Jsoup 공식 문서](https://jsoup.org/cookbook/)
- [Spring Data JPA 공식 문서](https://spring.io/projects/spring-data-jpa)

---

**개인 크롤링 학습용 프로젝트**  
*이 프로젝트는 학습 목적으로 제작되었습니다.*

---
## 시연 스크린샷

0. api 전체
![alt text](<0 스웨거 api 전체-1.png>)


1. 스크롤 기반 url 수집
![alt text](<1 스크롤 기반 url 수집.png>)
![alt text](<1 스크롤 기반 url 수집 콘솔.png>)

2. html 다운로드
![alt text](<2 html 다운로드 스웨거.png>)
![alt text](<2 html 다운 mysql스크린샷.png>)

3. html 로컬파싱
![alt text](<3 html 로컬 파싱.png>)


4. 이미지 다운로드
![alt text](<4 이미지 다운로드.png>)


5. 작업현황
![alt text](<5 작업현황.png>)


6. mysql 결과화면    
![alt text](<msyql 결과화면1 .png>)

![alt text](<mysql 결과화면2.png>)





















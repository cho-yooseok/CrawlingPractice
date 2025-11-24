package com.example.gugusnewcrawling.config;

import jakarta.annotation.PostConstruct;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Selenium WebDriver 설정 클래스
 * 
 * Chrome 브라우저 자동화를 위한 WebDriver를 설정하고 관리합니다.
 * - 로컬 환경: ChromeDriver 사용
 * - Docker 환경: RemoteWebDriver 사용 (Selenium Grid)
 * 
 * 봇 탐지 회피를 위한 다양한 옵션을 설정합니다:
 * - 자동화 배지 제거
 * - User-Agent 로테이션
 * - 랜덤 화면 크기
 * - 초기 마우스 움직임 시뮬레이션
 */
@Configuration
public class SeleniumConfig {

    /** Selenium Hub URL (Docker 환경에서 사용, 기본값: http://localhost:4444/wd/hub) */
    @Value("${selenium.hub.url:http://localhost:4444/wd/hub}")
    private String seleniumHubUrl;

    /** Remote WebDriver 사용 여부 (Docker 환경: true, 로컬 환경: false) */
    @Value("${selenium.use.remote:false}")
    private boolean useRemoteDriver;

    /**
     * Bean 생성 후 초기화 메서드
     * 
     * 로컬 환경인 경우 ChromeDriver 경로를 시스템 프로퍼티에 설정합니다.
     * Remote 환경인 경우 이 과정을 건너뜁니다.
     */
    @PostConstruct
    void postConstruct() {
        // Docker 환경이 아닌 경우에만 로컬 chromedriver 설정
        if (!useRemoteDriver) {
            String chromeDriverPath = "C:\\Users\\code\\Downloads\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe";
            System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        }
    }

    /**
     * WebDriver Bean 생성
     * 
     * 환경에 따라 로컬 ChromeDriver 또는 RemoteWebDriver를 반환합니다.
     * 
     * @return 설정된 WebDriver 인스턴스
     */
    @Bean
    public WebDriver webDriver() {
        ChromeOptions options = createChromeOptions();

        // Docker 환경에서는 RemoteWebDriver 사용
        if (useRemoteDriver) {
            try {
                return new RemoteWebDriver(new URL(seleniumHubUrl), options);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Selenium Hub URL이 올바르지 않습니다: " + seleniumHubUrl, e);
            }
        }

        // 로컬 환경에서는 ChromeDriver 사용
        WebDriver driver = new ChromeDriver(options);
        postCreateTuning(driver);
        return driver;
    }

    /**
     * Chrome 브라우저 옵션 생성
     * 
     * 봇 탐지 회피 및 안정성을 위한 다양한 Chrome 옵션을 설정합니다.
     * 
     * @return 설정된 ChromeOptions
     */
    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // --- 봇 탐지 우회 옵션 ---
        // Docker 환경에서는 headless 모드 권장 (GUI 없이 실행)
        if (useRemoteDriver) {
            options.addArguments("--headless");
        }

        // 자동화 배지 최소화 (navigator.webdriver 속성 감추기)
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");

        // 창 크기는 런타임에서 랜덤 적용 (고정 제거)

        // User-Agent 회전 (랜덤하게 선택된 User-Agent 사용)
        options.addArguments("user-agent=" + pickUserAgent());
        
        // --- Docker 환경을 위한 안정성 옵션 ---
        options.addArguments("--disable-gpu");              // GPU 비활성화 (Docker에서 필요)
        options.addArguments("--no-sandbox");               // 샌드박스 모드 비활성화
        options.addArguments("--disable-dev-shm-usage");    // 공유 메모리 사용 비활성화
        options.addArguments("--disable-extensions");       // 확장 프로그램 비활성화
        options.addArguments("--disable-plugins");          // 플러그인 비활성화

        // 프라이버시/성능 무리한 비활성화 제거(JS/이미지 유지)

        return options;
    }

    /**
     * WebDriver 생성 후 추가 튜닝
     * 
     * 봇 탐지를 더욱 효과적으로 회피하기 위한 후처리 작업:
     * - 랜덤 화면 크기 설정
     * - 초기 마우스 움직임 시뮬레이션
     * - navigator.webdriver 속성 우회 시도
     * 
     * @param driver 설정할 WebDriver 인스턴스
     */
    private void postCreateTuning(WebDriver driver) {
        // 창 크기 랜덤화 (1280~1920 x 720~1200 범위)
        Humanizer.randomViewportResize(driver, 1280, 1920, 720, 1200);
        
        // 초기 마우스 움직임 시뮬레이션 (사람처럼 보이게)
        Humanizer.subtleMouseMove(driver);
        
        // 짧은 대기 시간 (300~900ms)
        Humanizer.randomSleep(300, 900);
        
        // navigator.webdriver 속성 우회 시도 (가능한 경우)
        // JavaScript를 통해 navigator.webdriver를 undefined로 설정
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        } catch (Exception ignored) {
            // 실패해도 계속 진행
        }
    }

    /**
     * 랜덤 User-Agent 선택
     * 
     * 여러 브라우저의 User-Agent 중 하나를 랜덤하게 선택하여 반환합니다.
     * 봇 탐지를 회피하기 위해 다양한 User-Agent를 사용합니다.
     * 
     * @return 선택된 User-Agent 문자열
     */
    private String pickUserAgent() {
        List<String> agents = Arrays.asList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        );
        return agents.get(new Random().nextInt(agents.size()));
    }
}
package com.example.gugusnewcrawling.config;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Humanizer 유틸리티 클래스
 * 
 * 봇 탐지를 회피하기 위해 인간의 행동을 시뮬레이션하는 유틸리티 메서드를 제공합니다.
 * 
 * 주요 기능:
 * - 랜덤 대기 시간
 * - 부드러운 마우스 움직임
 * - 점진적 스크롤
 * - 랜덤 화면 크기 조정
 * - 요소에 마우스 호버
 * - 인간처럼 타이핑
 * 
 * 모든 메서드는 정적(static)이며 인스턴스화를 방지하기 위해 private 생성자를 사용합니다.
 */
public final class Humanizer {

    /** 안전한 랜덤 숫자 생성기 (암호화 수준) */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 인스턴스화 방지 */
    private Humanizer() {}

    /**
     * 랜덤한 시간만큼 대기
     * 
     * 지정된 최소/최대 시간 사이에서 랜덤하게 대기합니다.
     * 봇 탐지를 회피하기 위해 고정된 대기 시간 대신 랜덤 대기를 사용합니다.
     * 
     * @param minMs 최소 대기 시간 (밀리초)
     * @param maxMs 최대 대기 시간 (밀리초)
     */
    public static void randomSleep(int minMs, int maxMs) {
        int boundMin = Math.max(0, minMs);
        int sleepMs = boundMin + RANDOM.nextInt(Math.max(1, maxMs - boundMin + 1));
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 부드러운 마우스 움직임 시뮬레이션
     * 
     * 화면 내 랜덤한 위치로 마우스를 부드럽게 이동시킵니다.
     * 실제 사용자의 마우스 움직임처럼 보이게 하기 위한 메서드입니다.
     * 
     * @param driver WebDriver 인스턴스
     */
    public static void subtleMouseMove(WebDriver driver) {
        try {
            Dimension size = driver.manage().window().getSize();
            // 화면의 80% x 70% 범위 내에서 랜덤 위치 선택
            int x = 20 + RANDOM.nextInt(Math.max(1, (int) (size.width * 0.8)));
            int y = 80 + RANDOM.nextInt(Math.max(1, (int) (size.height * 0.7)));
            // 부드러운 마우스 움직임 + 랜덤 대기 (50~250ms)
            new Actions(driver).moveByOffset(1, 1).moveByOffset(x, y).pause(Duration.ofMillis(50 + RANDOM.nextInt(200))).perform();
        } catch (Exception ignored) {
            // 에러 발생 시 무시하고 계속 진행
        }
    }

    /**
     * 점진적 스크롤 (Human-like scrolling)
     * 
     * 한 번에 끝까지 스크롤하는 대신, 작은 단위로 나누어 점진적으로 스크롤합니다.
     * 실제 사용자가 스크롤하는 것처럼 보이게 합니다.
     * 
     * @param driver WebDriver 인스턴스
     * @param totalPixelsMin 최소 스크롤 픽셀 수
     * @param totalPixelsMax 최대 스크롤 픽셀 수
     */
    public static void gradualScroll(WebDriver driver, int totalPixelsMin, int totalPixelsMax) {
        // 총 스크롤할 픽셀 수 결정 (최소~최대 범위 내에서 랜덤)
        int total = totalPixelsMin + RANDOM.nextInt(Math.max(1, totalPixelsMax - totalPixelsMin + 1));
        int scrolled = 0;
        JavascriptExecutor js = (JavascriptExecutor) driver;
        
        // 작은 단위로 나누어 스크롤
        while (scrolled < total) {
            // 각 스크롤 단계: 150~400px
            int step = 150 + RANDOM.nextInt(250);
            js.executeScript("window.scrollBy(0, arguments[0]);", step);
            scrolled += step;
            // 스크롤 후 짧은 대기 (150~300ms)
            randomSleep(150, 300);
            
            // 위로 스크롤 비활성화 (무한 스크롤 깨짐 방지)
            // 주석 처리: 무한 스크롤 페이지에서 위로 스크롤하면 새 콘텐츠 로딩이 방해될 수 있음
            // if (RANDOM.nextDouble() < 0.08) {
            //     int up = 30 + RANDOM.nextInt(80);
            //     js.executeScript("window.scrollBy(0, arguments[0]);", -up);
            //     randomSleep(100, 200);
            // }
        }
        
        // 스크롤 완료 후 읽는 시간 시뮬레이션 (500~1500ms)
        randomSleep(500, 1500);
    }

    /**
     * 랜덤 화면 크기 조정
     * 
     * 브라우저 창 크기를 지정된 범위 내에서 랜덤하게 설정합니다.
     * 다양한 화면 해상도를 시뮬레이션하여 봇 탐지를 회피합니다.
     * 
     * @param driver WebDriver 인스턴스
     * @param minW 최소 너비
     * @param maxW 최대 너비
     * @param minH 최소 높이
     * @param maxH 최대 높이
     */
    public static void randomViewportResize(WebDriver driver, int minW, int maxW, int minH, int maxH) {
        try {
            // 랜덤한 너비와 높이 계산
            int w = minW + RANDOM.nextInt(Math.max(1, maxW - minW + 1));
            int h = minH + RANDOM.nextInt(Math.max(1, maxH - minH + 1));
            driver.manage().window().setSize(new Dimension(w, h));
            // 화면 크기 변경 후 짧은 대기 (200~700ms)
            randomSleep(200, 700);
        } catch (Exception ignored) {
            // 에러 발생 시 무시하고 계속 진행
        }
    }

    /**
     * 랜덤 요소에 마우스 호버
     * 
     * 지정된 CSS 선택자로 찾은 요소 중 하나를 랜덤하게 선택하여 마우스를 호버합니다.
     * 실제 사용자가 요소를 확인하는 행동을 시뮬레이션합니다.
     * 
     * @param driver WebDriver 인스턴스
     * @param locator 찾을 요소의 CSS 선택자
     */
    public static void hoverRandomly(WebDriver driver, By locator) {
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (elements.isEmpty()) return;
            // 요소 목록 중 하나를 랜덤하게 선택
            WebElement el = elements.get(ThreadLocalRandom.current().nextInt(elements.size()));
            // 선택된 요소로 마우스 이동 + 대기 (150~750ms)
            new Actions(driver).moveToElement(el).pause(Duration.ofMillis(150 + RANDOM.nextInt(600))).perform();
        } catch (Exception ignored) {
            // 에러 발생 시 무시하고 계속 진행
        }
    }

    /**
     * 인간처럼 타이핑
     * 
     * 문자열을 한 글자씩 입력하며, 각 글자 사이에 랜덤한 대기 시간을 둡니다.
     * 때때로 긴 대기 시간도 포함하여 실제 사람이 타이핑하는 것처럼 시뮬레이션합니다.
     * 
     * @param input 입력할 입력 필드 요소
     * @param text 입력할 텍스트
     */
    public static void typeHumanLike(WebElement input, String text) {
        for (char c : text.toCharArray()) {
            // 한 글자씩 입력
            input.sendKeys(String.valueOf(c));
            // 각 글자 사이에 짧은 대기 (40~180ms)
            randomSleep(40, 180);
            // 3% 확률로 긴 대기 시간 (200~500ms) - 생각하는 시간 시뮬레이션
            if (RANDOM.nextDouble() < 0.03) {
                randomSleep(200, 500);
            }
        }
    }
}
package com.example.gugusnewcrawling.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 스크래핑 진행 상황을 실시간으로 추적하는 싱글톤 컴포넌트.
 * 여러 스레드에서 안전하게 접근할 수 있습니다.
 */
@Component
public class ScrapingProgressTracker {

    // 멀티스레드 환경에서 원자적(atomic)으로 숫자를 증가시키기 위해 AtomicLong 사용
    private final AtomicLong inProgressUrlCount = new AtomicLong(0);

    /**
     * URL 수집 개수를 1 증가시킵니다.
     */
    public void increment() {
        inProgressUrlCount.incrementAndGet();
    }

    /**
     * 현재까지 수집된 URL 개수를 반환합니다.
     * @return 현재 수집 개수
     */
    public long getInProgressCount() {
        return inProgressUrlCount.get();
    }

    /**
     * 카운터를 0으로 초기화합니다. (새로운 수집 작업 시작 시 호출)
     */
    public void reset() {
        inProgressUrlCount.set(0);
    }
}
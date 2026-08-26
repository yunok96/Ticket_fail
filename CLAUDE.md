# 프로젝트 개요
동시성 제어 학습용 티켓팅 프로젝트. 영국 백엔드 취업 포트폴리오.
좌석 예약 시 발생하는 동시성 문제를 재현하고 해결하는 과정 자체가 결과물.
최종적으로 이 코드를 영어로 설명할 수 있어야 한다.

## 기술 스택
Java 17 / Spring Boot 3.x / Gradle / Spring Data JPA
DB: H2 (2단계 락 실험 시 Docker Postgres로 전환 예정)

## 진행 상태
- [x] 1단계: 락 없이 구현 → 동시 요청 100건으로 오버부킹 재현 (진행 중)
- [x] 2단계: 비관적 락 적용, before/after 비교
- [x] 3단계: 낙관적 락 적용, 낙관적 락 retry 버전과 비교
- [ ] 4단계: Redis 분산 락 적용, 낙관적 락과 비교
- [ ] 5단계: AWS 배포, README 정리

## 도메인
- Performance: totalSeats, reservedSeats (좌석 지정 없이 수량 카운터 방식)
- Reservation: performanceId, userId, reservedTime

# 작업 방식

## 절대 규칙
Entity, service, test 패키지 아래 파일은 절대 Edit/Write 하지 않는다.
문제가 보이면 수정하는 대신 무엇이 왜 잘못됐는지 설명하고, 내가 직접 고치게 한다.
내가 "네가 고쳐줘"라고 명시한 경우에만 예외.

## 허용
설정 파일(build.gradle, application.yml), Repository 인터페이스 등 보일러플레이트는 작성해도 된다.
테스트 실행: `./gradlew test`

## 언어
코드, 주석, 커밋 메시지, 로그, README는 영어.
나에게 하는 설명은 한국어.
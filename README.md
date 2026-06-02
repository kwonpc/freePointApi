# 무료 포인트 시스템 API

Java 21, Spring Boot 3.x, H2 기반으로 구현한 무료 포인트 시스템입니다.

구현 범위는 아래 4개 쓰기 API와 2개 조회 API입니다.

- 적립
- 적립취소
- 사용
- 사용취소
- 잔액 조회
- 원장 조회

## 산출물

- DB 설계 문서: [docs/db-design.md](/Users/gwon-ogeun/mssp/docs/db-design.md)
- ERD: [resource/erd.svg](/Users/gwon-ogeun/mssp/resource/erd.svg)
- ERD Mermaid 원본: [resource/erd.mmd](/Users/gwon-ogeun/mssp/resource/erd.mmd)
- AWS 아키텍처 다이어그램 SVG: [resource/aws-architecture.svg](/Users/gwon-ogeun/mssp/resource/aws-architecture.svg)
- AWS 아키텍처 다이어그램 PNG: [resource/aws-architecture.png](/Users/gwon-ogeun/mssp/resource/aws-architecture.png)
- H2 DDL: [src/main/resources/schema.sql](/Users/gwon-ogeun/mssp/src/main/resources/schema.sql)
- 별도 DDL 사본: [sql/schema.sql](/Users/gwon-ogeun/mssp/sql/schema.sql)

## 개발 환경

- Java 21
- Spring Boot 3.3.x
- Spring Web
- Spring Data JPA
- H2 Database
- Gradle 8.8

## 핵심 설계 포인트

### 1. 적립과 사용을 분리

- 적립 원장은 `point_grant`
- 사용 원장은 `point_use`
- 어떤 적립분이 어떤 사용건에서 얼마나 소진됐는지는 `point_use_allocation`

이 구조로 "특정 적립분이 어떤 주문에 사용됐는지"를 추적합니다.

### 2. 취소는 별도 이벤트로 저장

- 적립취소: `point_grant_cancel`
- 사용취소: `point_use_cancel`
- 사용취소 복구 상세: `point_use_cancel_allocation`

원본 행을 덮어쓰기보다 감사 추적과 부분취소 처리에 유리합니다.

### 3. 멱등성 보장

- 멱등 요청 원장: `point_transaction_request`
- 기준 키: `userId + transactionNo + API 종류`
- 같은 요청이 재전송되면 기존 응답 재사용
- 같은 `transactionNo`라도 payload가 다르면 `409 Conflict`

### 4. 정책값 하드코딩 제거

아래 값은 `point_policy` 테이블에서 읽습니다.

- 1회 최대 적립 한도
- 개인 최대 보유 한도
- 기본 만료일
- 최소/최대 만료일

### 5. 사용 우선순위

- 수기 지급 포인트 우선
- 같은 그룹에서는 만료일이 빠른 순
- 같은 만료일이면 먼저 적립된 순

### 6. 만료 후 사용취소 처리

사용취소 시 원래 사용했던 적립분이 이미 만료됐다면:

- 원 적립분으로 잔액 복구하지 않음
- `REGRANT_FROM_USE_CANCEL` 타입의 신규 적립 생성

## 구현된 API

### 쓰기 API

- `POST /api/points/grants`
- `POST /api/points/grant-cancels`
- `POST /api/points/uses`
- `POST /api/points/use-cancels`

### 조회 API

- `GET /api/points/balance?userId={userId}`
- `GET /api/points/ledger?userId={userId}`

## 요청/응답 예시

### 1. 적립

```http
POST /api/points/grants
Content-Type: application/json
```

```json
{
  "userId": "user-1",
  "transactionNo": "grant-001",
  "amount": 1000,
  "manual": true,
  "sourceRef": "ADMIN-001",
  "expiryDays": 365
}
```

### 2. 사용

```http
POST /api/points/uses
Content-Type: application/json
```

```json
{
  "userId": "user-1",
  "transactionNo": "use-001",
  "orderNo": "A1234",
  "amount": 700
}
```

### 3. 공통 성공 응답

```json
{
  "success": true,
  "data": {
    "operation": "USE",
    "userId": "user-1",
    "transactionNo": "use-001",
    "pointKey": "generatedPointKey",
    "targetPointKey": null,
    "orderNo": "A1234",
    "amount": 700,
    "balance": 300
  },
  "error": null
}
```

### 4. 멱등성 충돌 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "동일 transactionNo 에 서로 다른 요청이 들어왔습니다."
  }
}
```

## 예외 코드

현재 주요 예외 코드는 아래와 같습니다.

- `INVALID_REQUEST`
- `ACCOUNT_NOT_FOUND`
- `POINT_NOT_FOUND`
- `POINT_OWNER_MISMATCH`
- `GRANT_LIMIT_EXCEEDED`
- `BALANCE_LIMIT_EXCEEDED`
- `INVALID_EXPIRY_DAYS`
- `INSUFFICIENT_BALANCE`
- `GRANT_ALREADY_USED`
- `GRANT_ALREADY_CANCELED`
- `INVALID_CANCEL_AMOUNT`
- `IDEMPOTENCY_CONFLICT`

## 실행 방법

### 1. 테스트 실행

```bash
gradle test
```

### 2. 애플리케이션 실행

```bash
gradle bootRun
```

### 3. Swagger UI

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/api-docs`

### 4. H2 콘솔

- URL: `/h2-console`
- JDBC URL: `jdbc:h2:mem:free-point-db`

## 검증한 시나리오

테스트 파일: [PointCommandControllerTest.java](/Users/gwon-ogeun/mssp/src/test/java/com/assignment/freepoints/point/api/PointCommandControllerTest.java)

### 멱등성/원장

- 동일 적립 요청 재호출 시 멱등 응답 반환
- 같은 `transactionNo`에 다른 payload 요청 시 `409 Conflict`
- 만료된 적립분을 사용한 뒤 부분 사용취소하면 신규 적립으로 복구
- 잔액 조회와 원장 조회 결과 확인

### 사용 우선순위

- 사용 시 수기 지급분 우선 → 만료 빠른 순으로 적립분이 소진됨

### 예외 처리

- 잔액 부족 시 `INSUFFICIENT_BALANCE`
- 계정이 없는 사용 요청 시 `ACCOUNT_NOT_FOUND` (404)
- 개인 최대 보유 한도 초과 적립 시 `BALANCE_LIMIT_EXCEEDED`
- 적립취소 정상 처리 후 동일 적립건 재취소 시 `GRANT_ALREADY_CANCELED`
- 이미 사용 이력이 있는 적립건 취소 시 `GRANT_ALREADY_USED`
- 다른 회원의 포인트 취소 시 `POINT_OWNER_MISMATCH`
- 취소 가능 금액을 초과한 사용취소 시 `INVALID_CANCEL_AMOUNT`
- 존재하지 않는 포인트 키 사용취소 시 `POINT_NOT_FOUND` (404)
- 빈 값/0 금액/길이 초과 등 잘못된 요청 시 `INVALID_REQUEST`

## 프로젝트 구조

- 애플리케이션 시작점: [FreePointSystemApplication.java](/Users/gwon-ogeun/mssp/src/main/java/com/assignment/freepoints/FreePointSystemApplication.java)
- 쓰기 API: [PointCommandController.java](/Users/gwon-ogeun/mssp/src/main/java/com/assignment/freepoints/point/api/PointCommandController.java)
- 조회 API: [PointQueryController.java](/Users/gwon-ogeun/mssp/src/main/java/com/assignment/freepoints/point/api/PointQueryController.java)
- 핵심 서비스: [PointCommandService.java](/Users/gwon-ogeun/mssp/src/main/java/com/assignment/freepoints/point/service/PointCommandService.java), [PointQueryService.java](/Users/gwon-ogeun/mssp/src/main/java/com/assignment/freepoints/point/service/PointQueryService.java)
- 설정: [application.yml](/Users/gwon-ogeun/mssp/src/main/resources/application.yml)
- 스키마: [schema.sql](/Users/gwon-ogeun/mssp/src/main/resources/schema.sql)

## 제한 사항

- 현재는 H2 메모리 DB 기준입니다.
- 인증/인가, 관리자 권한 분리, 배치 만료 처리 스케줄러는 포함하지 않았습니다.
- 원장 조회는 과제 제출용 단순 조회 형태이며, 운영용 페이징/필터링은 추가 구현이 필요합니다.
- 현재 머신에서는 `./gradlew` 가 Java truststore SSL 문제로 배포본 다운로드 단계에서 실패할 수 있습니다. 검증은 `gradle test` 로 수행했습니다.

## 다음 확장 포인트

- 만료 배치 스케줄러 추가
- 관리자 지급 API와 사용자 주문 API 권한 분리
- 원장 조회 페이징/필터링
- OpenAPI/Swagger 문서화
- 운영 DB 기준 락 전략 및 인덱스 재조정

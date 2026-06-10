# 면접 대응 요약

## 1. 왜 적립/사용/취소를 테이블로 분리했나

핵심 요구사항이 "특정 시점에 적립된 포인트가 어떤 주문에서 얼마만큼 사용됐는지 추적"하는 것이었기 때문입니다.

- 적립 원장: `point_grant`
- 사용 원장: `point_use`
- 사용 상세 배분: `point_use_allocation`
- 적립취소: `point_grant_cancel`
- 사용취소: `point_use_cancel`
- 사용취소 복구 상세: `point_use_cancel_allocation`

잔액만 관리하는 단일 테이블 구조로는 부분 사용취소, 만료 후 재적립, 주문별 추적을 동시에 만족시키기 어렵습니다.

## 2. 왜 멱등성 테이블을 따로 뒀나

API 중복 호출은 포인트 원장과 별도로 관리해야 합니다.

- 요청 원장: `point_transaction_request`
- 기준 키: `userId + transactionNo + API 종류`
- 동일 payload 재호출이면 기존 응답 반환
- 동일 `transactionNo`에 다른 payload가 들어오면 `409 Conflict`

이렇게 하면 네트워크 재시도와 실제 중복 처리 버그를 구분할 수 있습니다.

## 3. 왜 포인트 계정에 비관적 락을 걸었나

포인트는 금액성 데이터라 동시성보다 정합성을 우선했습니다.

같은 회원에 대해 적립, 사용, 취소 요청이 동시에 들어오면 잔액과 원장이 꼬일 수 있습니다. 그래서 `point_account`를 `PESSIMISTIC_WRITE`로 조회해서 회원 단위 쓰기 작업을 직렬화했습니다.

트래픽이 매우 커지면 낙관적 락이나 큐 기반 처리도 검토할 수 있지만, 이번 과제 범위에서는 비관적 락이 단순하고 안전합니다.

## 4. 낙관적 락으로 바꾼다면 어떻게 하나

`point_account.version` 컬럼과 JPA `@Version`을 이용할 수 있습니다.

동시 요청이 같은 버전을 읽고 처리하더라도, 마지막 저장 시점에 버전이 다르면 충돌이 발생합니다. 이때 재시도하거나 `409 Conflict`로 응답하면 됩니다.

다만 이 프로젝트는 잔액뿐 아니라 `grant`, `use`, `allocation` 원장을 같이 쓰므로, 낙관적 락을 쓸 때는 재시도 과정에서 중복 원장이 생기지 않도록 멱등성 처리와 함께 설계해야 합니다.

## 5. 사용 우선순위는 어떻게 처리했나

요구사항대로 아래 순서로 사용합니다.

1. 관리자 수기 지급 포인트 우선
2. 만료일이 빠른 포인트 우선
3. 같은 만료일이면 먼저 적립된 포인트 우선

이 우선순위로 `point_grant`를 조회하고, 실제 차감 내역은 `point_use_allocation`에 남깁니다.

## 6. 사용취소 시 만료된 포인트는 왜 신규 적립으로 복구하나

요구사항에 "사용취소 시점에 이미 만료된 포인트를 사용취소해야 한다면 그 금액만큼 신규적립 처리"라고 되어 있기 때문입니다.

- 원 적립분이 아직 유효하면 `remaining_amount` 복구
- 원 적립분이 이미 만료됐으면 `REGRANT_FROM_USE_CANCEL` 타입의 신규 적립 생성

이력상 원래 어떤 사용취소에서 재적립됐는지도 `original_use_cancel_id`와 `point_use_cancel_allocation`으로 추적할 수 있습니다.

## 7. 왜 정책 테이블을 뒀나

과제 요구사항에 "하드코딩이 아닌 방식으로 제어"해야 하는 값들이 있습니다.

정책 테이블 `point_policy`로 관리하는 값:

- `MAX_GRANT_PER_TX`: 1회 최대 적립 금액
- `MIN_USE_PER_TX`: 1회 최소 사용 금액
- `MAX_USE_PER_TX`: 1회 최대 사용 금액
- `MAX_BALANCE_PER_USER`: 개인 최대 보유 금액
- `DEFAULT_EXPIRY_DAYS`: 기본 만료일
- `MIN_EXPIRY_DAYS`: 최소 만료일
- `MAX_EXPIRY_DAYS`: 최대 만료일

현재 사용 API의 최소/최대 한도도 DTO 어노테이션이 아니라 정책값으로 검증합니다.

## 8. 왜 도메인 중심 패키지 구조를 썼나

전통적인 `controller`, `service`, `repository`, `entity` 계층형 구조도 가능하지만, 기능이 커지면 관련 파일을 여러 패키지에서 찾아야 합니다.

이번 프로젝트는 포인트 적립, 사용, 취소 흐름이 강하게 연결되어 있어서 도메인 중심으로 묶었습니다.

- `point/account`
- `point/grant`
- `point/use`
- `point/transaction`
- `point/policy`
- `point/api`
- `point/service`

기능 단위 응집도를 높이고 변경 영향 범위를 파악하기 쉽게 하기 위한 선택입니다.

## 9. 현재 구현의 한계

- H2 메모리 DB 기준
- 인증/인가 미적용
- 관리자 권한 분리 미적용
- 만료 배치 스케줄러 미구현
- 원장 조회 페이징/필터링 미구현
- 운영 환경 기준 모니터링은 아키텍처 산출물 수준으로만 반영

## 10. 운영 환경으로 확장한다면

- DB를 RDS PostgreSQL 또는 MySQL로 전환
- EventBridge Scheduler 또는 배치 잡으로 만료 처리
- CloudWatch/X-Ray 기반 모니터링
- 관리자 API와 주문 API 인증/인가 분리
- 원장 조회에 기간, 타입, 주문번호 필터와 페이징 추가
- 트래픽 패턴에 따라 비관적 락, 낙관적 락, 큐 기반 직렬화 비교

# 무료 포인트 시스템 DB 설계

## 1. 설계 목표

이 설계는 아래 5가지를 우선 보장하도록 잡았습니다.

1. 적립된 포인트가 어떤 주문에서 얼마만큼 사용되었는지 1포인트 단위로 추적 가능해야 한다.
2. 적립취소는 "해당 적립분이 전혀 사용되지 않은 경우"에만 가능해야 한다.
3. 사용취소는 부분취소를 지원해야 하고, 이미 만료된 적립분이라면 원복 대신 신규적립으로 복구해야 한다.
4. 최대 적립 한도, 개인 보유 한도, 기본 만료일은 하드코딩하지 않고 정책 테이블로 관리해야 한다.
5. 적립, 적립취소, 사용, 사용취소 API 는 중복 호출되어도 `transaction_no` 기준으로 멱등하게 처리되어야 한다.

## 2. 모델링 원칙

### 2.1 적립과 사용을 분리

- 적립은 `point_grant` 에 저장한다.
- 사용은 `point_use` 에 저장한다.
- 어떤 적립이 어떤 사용에 소진되었는지는 `point_use_allocation` 으로 추적한다.

### 2.2 취소는 원본 행을 수정하지 않고 별도 이벤트로 기록

- 적립취소는 `point_grant_cancel`
- 사용취소는 `point_use_cancel`
- 사용취소 시 어떤 할당분이 어떻게 복구되었는지는 `point_use_cancel_allocation`

이 방식이 감사 추적과 부분취소 처리에 유리합니다.

### 2.3 현재 잔액 컬럼은 조회 최적화용

- `point_account.balance`
- `point_grant.remaining_amount`
- `point_use.cancelable_amount`

모두 원장성 이력으로부터 계산 가능한 값이지만, API 처리와 검증을 단순하게 만들기 위해 유지합니다.

### 2.4 멱등성은 요청 원장으로 분리

- 포인트 원장과 별도로 `point_transaction_request` 를 둔다.
- 클라이언트는 적립, 적립취소, 사용, 사용취소 요청마다 고유한 `transaction_no` 를 보낸다.
- 서버는 `member + transaction_type + transaction_no` 를 유니크하게 보장한다.
- 동일 요청의 재전송이면 기존 처리 결과를 그대로 반환한다.
- 같은 `transaction_no` 이지만 payload 가 다르면 중복 성공이 아니라 충돌 오류로 처리한다.

## 3. 엔티티

### 3.1 point_account

회원별 포인트 지갑입니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| user_id | VARCHAR(64) | 회원 식별자, UK |
| balance | BIGINT | 현재 사용 가능한 총 잔액 |
| version | BIGINT | 낙관적 락 버전 |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

### 3.2 point_policy

하드코딩 금지 조건을 위한 정책 테이블입니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| policy_code | VARCHAR(50) | 정책 코드, UK |
| policy_value | VARCHAR(100) | 정책값 |
| value_type | VARCHAR(20) | NUMBER, DAYS 등 |
| description | VARCHAR(255) | 설명 |
| active | BOOLEAN | 사용 여부 |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

권장 정책 코드:

- `MAX_GRANT_PER_TX`: 1회 최대 적립 가능 포인트
- `MAX_BALANCE_PER_USER`: 개인 최대 보유 가능 포인트
- `DEFAULT_EXPIRY_DAYS`: 기본 만료일
- `MIN_EXPIRY_DAYS`: 최소 만료일
- `MAX_EXPIRY_DAYS`: 최대 만료일

### 3.3 point_transaction_request

API 멱등성 제어용 요청 원장입니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| account_id | BIGINT | FK -> point_account.id |
| transaction_type | VARCHAR(30) | `GRANT`, `GRANT_CANCEL`, `USE`, `USE_CANCEL` |
| transaction_no | VARCHAR(100) | 클라이언트가 보낸 거래번호 |
| request_hash | VARCHAR(128) | 주요 요청 필드를 정규화 후 해시한 값 |
| request_amount | BIGINT | 요청 금액 |
| request_order_no | VARCHAR(100) | 사용/사용취소 시 주문번호 |
| target_point_key | VARCHAR(40) | 취소 대상 pointKey |
| status | VARCHAR(20) | `PROCESSING`, `SUCCEEDED`, `FAILED` |
| result_point_key | VARCHAR(40) | 성공 시 생성/처리된 대표 pointKey |
| error_code | VARCHAR(50) | 실패 코드 |
| response_snapshot | CLOB | 재응답용 결과 요약 JSON |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

제약:

- `unique(account_id, transaction_type, transaction_no)`
- 같은 키로 재시도 시 `request_hash` 가 다르면 충돌 처리
- `PROCESSING` 상태는 동시 실행 차단용 잠금 의미를 가진다.

### 3.4 point_grant

적립 원천 테이블입니다. "사용 가능한 포인트 덩어리"의 기준 단위입니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| point_key | VARCHAR(40) | 외부 노출용 키, UK |
| transaction_request_id | BIGINT | FK -> point_transaction_request.id, UK |
| account_id | BIGINT | FK -> point_account.id |
| grant_type | VARCHAR(30) | `NORMAL`, `MANUAL`, `REGRANT_FROM_USE_CANCEL` |
| source_ref | VARCHAR(100) | 외부 참조값. 관리지급 ID 등 |
| granted_amount | BIGINT | 적립 금액 |
| remaining_amount | BIGINT | 미사용 잔액 |
| expires_at | TIMESTAMP | 만료일시 |
| status | VARCHAR(20) | `ACTIVE`, `CANCELED`, `EXPIRED`, `DEPLETED` |
| original_use_cancel_id | BIGINT | 만료 후 사용취소로 재적립된 경우 FK -> point_use_cancel.id |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

제약:

- `granted_amount > 0`
- `0 <= remaining_amount <= granted_amount`
- `expires_at >= created_at + 1 day`
- `expires_at < created_at + 5 years`

### 3.5 point_grant_cancel

적립취소 이력입니다. 요구사항상 원 적립분이 일부라도 사용되면 취소 불가이므로 사실상 "전액 취소"입니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| point_key | VARCHAR(40) | 외부 노출용 키, UK |
| transaction_request_id | BIGINT | FK -> point_transaction_request.id, UK |
| grant_id | BIGINT | FK -> point_grant.id |
| canceled_amount | BIGINT | 취소 금액, 원칙적으로 원 적립금액과 동일 |
| reason | VARCHAR(255) | 취소 사유 |
| created_at | TIMESTAMP | 생성일시 |

업무 규칙:

- `point_grant.remaining_amount == point_grant.granted_amount` 인 경우만 생성 가능
- 적립취소 완료 후 `point_grant.status = CANCELED`
- 계정 잔액 차감

### 3.6 point_use

주문에서 발생한 포인트 사용 이벤트입니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| point_key | VARCHAR(40) | 외부 노출용 키, UK |
| transaction_request_id | BIGINT | FK -> point_transaction_request.id, UK |
| account_id | BIGINT | FK -> point_account.id |
| order_no | VARCHAR(100) | 주문번호 |
| used_amount | BIGINT | 사용 금액 |
| cancelable_amount | BIGINT | 아직 취소 가능한 잔액 |
| status | VARCHAR(20) | `USED`, `PARTIALLY_CANCELED`, `CANCELED` |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

제약:

- `used_amount > 0`
- `0 <= cancelable_amount <= used_amount`
- `(account_id, order_no)` 인덱스 권장

### 3.7 point_use_allocation

한 번의 사용이 어떤 적립분에서 얼마나 차감되었는지 저장합니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| use_id | BIGINT | FK -> point_use.id |
| grant_id | BIGINT | FK -> point_grant.id |
| allocated_amount | BIGINT | 해당 적립분에서 사용된 금액 |
| canceled_amount | BIGINT | 이 할당분 중 사용취소로 복구된 금액 누계 |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

제약:

- `allocated_amount > 0`
- `0 <= canceled_amount <= allocated_amount`
- 한 사용건의 `allocated_amount` 총합은 `point_use.used_amount` 와 같아야 함

사용 우선순위:

1. `grant_type = MANUAL` 우선
2. 같은 그룹 안에서는 `expires_at ASC`
3. 같은 만료일이면 `id ASC`

### 3.8 point_use_cancel

사용 취소 이벤트입니다. 전체/부분 취소를 모두 지원합니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| point_key | VARCHAR(40) | 외부 노출용 키, UK |
| transaction_request_id | BIGINT | FK -> point_transaction_request.id, UK |
| use_id | BIGINT | FK -> point_use.id |
| canceled_amount | BIGINT | 취소 금액 |
| reason | VARCHAR(255) | 취소 사유 |
| created_at | TIMESTAMP | 생성일시 |

업무 규칙:

- `canceled_amount > 0`
- `canceled_amount <= point_use.cancelable_amount`

### 3.9 point_use_cancel_allocation

사용취소 금액이 어느 원천 적립분으로 복구되었는지 저장합니다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT | PK |
| use_cancel_id | BIGINT | FK -> point_use_cancel.id |
| use_allocation_id | BIGINT | FK -> point_use_allocation.id |
| canceled_amount | BIGINT | 해당 할당분에서 취소된 금액 |
| restore_type | VARCHAR(30) | `RETURN_TO_GRANT`, `REGRANT_NEW_POINT` |
| restored_grant_id | BIGINT | 만료되어 신규적립한 경우 FK -> point_grant.id |
| created_at | TIMESTAMP | 생성일시 |

의미:

- `RETURN_TO_GRANT`: 원 적립분이 아직 유효해서 `point_grant.remaining_amount` 로 되돌림
- `REGRANT_NEW_POINT`: 원 적립분이 만료되어 신규 적립행 생성

## 4. 관계 요약

- `point_account 1 : N point_transaction_request`
- `point_account 1 : N point_grant`
- `point_account 1 : N point_use`
- `point_transaction_request 1 : 0..N point_grant`
- `point_transaction_request 1 : 0..1 point_grant_cancel`
- `point_transaction_request 1 : 0..1 point_use`
- `point_transaction_request 1 : 0..1 point_use_cancel`
- `point_grant 1 : 0..1 point_grant_cancel`
- `point_use 1 : N point_use_allocation`
- `point_use 1 : N point_use_cancel`
- `point_use_allocation 1 : N point_use_cancel_allocation`
- `point_use_cancel 1 : N point_use_cancel_allocation`
- `point_use_cancel_allocation N : 0..1 point_grant` via `restored_grant_id`

## 5. 주요 업무 규칙

### 5.1 적립

- 정책 테이블에서 `MAX_GRANT_PER_TX`, `MAX_BALANCE_PER_USER`, 만료일 정책을 조회한다.
- 요청 시작 시 `point_transaction_request(GRANT, transaction_no)` 를 먼저 확보한다.
- 동일 `transaction_no` 재호출이면 `request_hash` 비교 후 기존 성공 결과 반환 또는 충돌 처리
- 적립 생성 후 `point_account.balance` 증가
- 수기지급이면 `grant_type = MANUAL`
- 기본 만료일은 정책값 `DEFAULT_EXPIRY_DAYS`

### 5.2 적립취소

- 요청 시작 시 `point_transaction_request(GRANT_CANCEL, transaction_no)` 를 먼저 확보한다.
- 대상 적립행이 존재해야 한다.
- 이미 일부라도 사용되었다면 취소 불가
- 취소 성공 시 `point_grant_cancel` 기록

### 5.3 사용

- 요청 시작 시 `point_transaction_request(USE, transaction_no)` 를 먼저 확보한다.
- 주문번호는 필수
- 수기지급 포인트 우선, 이후 만료일 빠른 순으로 차감
- 차감 내역은 `point_use_allocation` 에 분해 저장

### 5.4 사용취소

- 요청 시작 시 `point_transaction_request(USE_CANCEL, transaction_no)` 를 먼저 확보한다.
- 전체/부분 취소 가능
- 취소 대상 사용건의 남은 `cancelable_amount` 이내여야 함
- 원래 차감했던 `point_use_allocation` 생성 순서대로 복구
- 복구 대상 적립분이 이미 만료되었다면 원복하지 않고 `grant_type = REGRANT_FROM_USE_CANCEL` 적립 생성

### 5.5 멱등성 처리 규칙

모든 쓰기 API 는 아래 순서로 처리합니다.

1. `account_id + transaction_type + transaction_no` 로 `point_transaction_request` 조회
2. 없으면 `PROCESSING` 상태로 insert
3. 있으면 `request_hash` 비교
4. 해시가 다르면 `409 Conflict`
5. `SUCCEEDED` 이면 저장된 `response_snapshot` 또는 `result_point_key` 로 동일 응답 반환
6. `PROCESSING` 이면 이미 처리중인 요청으로 보고 짧게 재시도시키거나 `202/409` 성격의 응답 반환
7. 실제 포인트 처리 성공 후 관련 원장과 함께 `SUCCEEDED` 로 갱신

`request_hash` 에는 최소한 아래 필드를 포함하는 편이 안전합니다.

- 적립: `user_id`, `amount`, `grant_type`, `expires_at`
- 적립취소: `user_id`, `target_point_key`
- 사용: `user_id`, `order_no`, `amount`
- 사용취소: `user_id`, `target_use_point_key`, `amount`

즉, `transaction_no` 만 같다고 무조건 같은 요청으로 보면 안 되고, 의미가 같은 요청인지까지 확인해야 합니다.

## 6. 예시 시나리오 반영

요구사항 예시는 아래처럼 저장됩니다.

1. A 적립 1000
   - `point_grant(A, granted=1000, remaining=1000)`
2. B 적립 500
   - `point_grant(B, granted=500, remaining=500)`
3. C 사용 1200, 주문번호 `A1234`
   - `point_use(C, used=1200, cancelable=1200)`
   - `point_use_allocation(C->A, 1000)`
   - `point_use_allocation(C->B, 200)`
   - A 잔액 0, B 잔액 300
4. A 만료
   - `point_grant(A).status = EXPIRED`
5. D 사용취소 1100
   - `point_use_cancel(D, canceled=1100)`
   - A 할당 1000 취소 시 A는 만료됨
   - `point_grant(E, grant_type=REGRANT_FROM_USE_CANCEL, granted=1000, remaining=1000)`
   - `point_use_cancel_allocation(D, allocation=C->A, 1000, REGRANT_NEW_POINT, restored_grant_id=E)`
   - `point_use_cancel_allocation(D, allocation=C->B, 100, RETURN_TO_GRANT, restored_grant_id=null)`
   - B 잔액 300 -> 400
   - C의 `cancelable_amount = 100`

## 7. 인덱스 권장안

- `point_account(user_id)` unique
- `point_policy(policy_code)` unique
- `point_transaction_request(account_id, transaction_type, transaction_no)` unique
- `point_transaction_request(status, updated_at)`
- `point_grant(point_key)` unique
- `point_grant(transaction_request_id)` unique
- `point_grant(account_id, status, grant_type, expires_at)`
- `point_grant(account_id, expires_at)`
- `point_grant_cancel(transaction_request_id)` unique
- `point_use(point_key)` unique
- `point_use(transaction_request_id)` unique
- `point_use(account_id, order_no)`
- `point_use_allocation(use_id)`
- `point_use_allocation(grant_id)`
- `point_use_cancel(point_key)` unique
- `point_use_cancel(transaction_request_id)` unique
- `point_use_cancel(use_id)`

## 8. 구현 시 주의점

- 동시성 제어가 중요하므로 `point_account.version` 기반 낙관적 락 또는 계정 단위 비관적 락을 고려해야 합니다.
- 멱등성과 동시성을 같이 보려면 `point_transaction_request` 생성과 계정 잔액 변경을 같은 DB 트랜잭션 안에서 처리해야 합니다.
- `transaction_no` 는 전역 유니크까지 갈 필요는 없고, 보통 `회원 + 거래타입` 범위 유니크면 충분합니다.
- 네트워크 타임아웃 후 재시도 상황을 위해 성공 응답의 핵심 필드는 `response_snapshot` 에 보관하는 편이 안전합니다.
- 잔액 컬럼은 정합성 검증 배치 또는 테스트 케이스로 지속 확인해야 합니다.
- 만료 배치는 `point_grant.remaining_amount > 0` 인 행만 대상으로 처리하면 됩니다.
- H2를 쓰더라도 금액은 `BIGINT` 로 두는 편이 단순합니다.


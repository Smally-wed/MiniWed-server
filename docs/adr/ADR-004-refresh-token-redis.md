# ADR-004: Refresh 토큰은 RDB가 아닌 Redis에 저장한다

- 상태: 승인됨
- 작성일: 2026-07-13
- 작성자: jasmin
- 관련 ADR: ADR-003

## 배경 (Context)

ADR-003에서 인증을 JWT(access + refresh) 구조로 정하면서, refresh 토큰의 **저장·회전 정책**은 후속 과제로 남겨두었다. ERD(§5) 역시 "refresh 토큰 저장소: RDB vs Redis"를 미확정 항목으로 명시했고, 초안 ERD에서는 `refresh_tokens` 테이블을 **잠정 RDB 안**으로만 제시했다.

이제 인증 도메인 엔티티를 구현하는 시점이 되어 refresh 토큰 저장소를 확정해야 한다. refresh 토큰은 다음 특성을 가진다.

- **TTL이 본질적이다.** 만료되면 더 이상 쓸모가 없으므로 자동 삭제가 바람직하다.
- **조회 패턴이 단순하다.** 사용자/토큰 식별자로 단건 조회·삭제(회전·로그아웃)가 대부분이고, 복잡한 조인·집계가 없다.
- **쓰기가 잦다.** 로그인·토큰 회전마다 갱신된다.
- **영속 데이터가 아니다.** 유실되어도 사용자는 재로그인하면 되며, 비즈니스 데이터가 아니다.

한편 프로젝트에는 이미 Redis가 도입되어 있다(로컬 docker-compose + `spring-boot-starter-data-redis`, `RedisConfig`).

## 결정 (Decision)

Refresh 토큰은 **RDB(`refresh_tokens` 테이블)가 아닌 Redis에 저장한다.** Spring Data Redis의 `@RedisHash`를 사용해 `RefreshToken`을 Redis 해시로 매핑하고, `@TimeToLive`로 토큰 만료와 Redis 키 만료를 일치시켜 만료분이 자동 삭제되게 한다.

이에 따라 ERD의 `refresh_tokens` 테이블과 `users ||--o{ refresh_tokens` 관계는 **RDB 스키마에서 제외**한다. `RefreshToken`은 JPA `@Entity`가 아니라 Redis 전용 모델이 된다.

## 고려한 대안 (Alternatives Considered)

### 대안 1: RDB 테이블에 저장 (ERD 초안 안)
- 설명: `refresh_tokens` 테이블에 토큰 해시·만료·revoked 플래그를 저장.
- 장점: 하나의 저장소로 일관 관리, 트랜잭션·조인 가능, 감사(audit)에 유리.
- 단점: 만료 토큰 정리를 위한 별도 배치/스케줄러가 필요(자동 TTL 없음). 잦은 쓰기가 RDB 부하로 이어진다. 단순 key 조회에 RDB는 과하다.
- 채택하지 않은 이유: refresh 토큰은 TTL이 본질이고 조회가 단순한 휘발성 데이터라, 자동 만료·빠른 조회를 제공하는 Redis가 더 적합하다.

### 대안 2: Redis에 저장 (채택)
- 설명: `@RedisHash` + `@TimeToLive`로 토큰을 Redis에 저장, 만료 시 자동 삭제.
- 장점: TTL 자동 만료로 정리 로직 불필요. 조회·삭제가 빠르다. 이미 도입된 인프라를 활용. 로그아웃·회전 시 key 삭제만으로 즉시 무효화.
- 단점: 저장소가 둘(RDB+Redis)로 나뉘어 운영 포인트가 늘어난다. Redis 유실 시 전체 세션이 끊긴다(단, 재로그인으로 복구 가능). RDB 트랜잭션과 묶이지 않는다.
- 채택한 이유: 위 장점이 refresh 토큰의 특성과 정확히 맞고, 단점(유실 시 재로그인)은 감수 가능한 수준이다.

## 결정 이유 (Rationale)

- **데이터 성격 적합성**: refresh 토큰은 만료·회전되는 휘발성 세션 데이터이지 영속 비즈니스 데이터가 아니다. TTL 자동 만료가 핵심 요구이며 Redis가 이를 기본 제공한다.
- **운영 단순화**: RDB 안은 만료분 정리 배치가 필요하지만, Redis는 키 만료로 자동 처리되어 정리 로직 자체가 사라진다.
- **성능**: 로그인·회전마다 발생하는 잦은 쓰기와 단건 조회에 인메모리 저장소가 유리하다.
- **기존 인프라 재사용**: 이미 Redis가 도입돼 있어 추가 비용이 작다.

## 영향 (Consequences)

### 긍정적 영향
- 만료 토큰 정리 배치가 필요 없다(TTL 자동 삭제).
- 로그아웃·토큰 회전 시 key 삭제만으로 즉시 무효화된다.
- RDB 스키마가 단순해진다(`refresh_tokens` 테이블 제거).

### 부정적 영향 / 트레이드오프
- 저장소가 RDB+Redis 둘로 나뉘어, 인증 상태 추적이 두 곳에 걸친다.
- Redis 장애·재시작(비영속 구성 시)으로 저장분이 사라지면 사용자는 재로그인해야 한다. 영속이 필요하면 Redis AOF/RDB 스냅샷 구성으로 완화한다.
- RDB 트랜잭션과 원자적으로 묶이지 않으므로, 가입/로그인 흐름에서 정합성 처리를 코드로 신경 써야 한다.

### 후속 조치 필요 사항
- refresh 토큰 회전(rotation) 상세: 재발급 시 이전 토큰 무효화, 재사용 감지(reuse detection) 정책.
- Redis 키 설계: `refreshToken:{userId}` 다중 세션 지원 여부(기기별 토큰), 값에 담을 필드(토큰 해시, 만료, 발급 시각).
- Redis 영속성 구성(AOF/RDB) 및 운영 환경 가용성 정책.
- ERD.md에서 `refresh_tokens` 테이블·관계 제거 반영(승인 후).

## 참고 자료 (References)

- [ADR-003](./ADR-003-authentication-jwt-oauth2.md) — JWT 인증, refresh 저장·회전을 후속 과제로 명시
- [docs/ERD.md](../ERD.md) §5 — refresh 토큰 저장소 미확정 항목
- Spring Data Redis `@RedisHash` / `@TimeToLive` 문서

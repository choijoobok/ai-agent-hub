---
id: LLM-FWK-BE-001
title: "영속 — JPA / QueryDSL / MyBatis 경계와 DB 중립"
category: standard/backend
type: operation
class: policy
created: 2026-05-04
updated: 2026-09-07
status: active
sources: ["sources/backend/domain/", "sources/backend/core/search/", "aidd-guide/standard/backend/packages.md", "sources/backend/core/domain/"]
tier: 2
tags: [backend, jpa, querydsl, mybatis, liquibase, portability]
---

# 영속 — JPA / QueryDSL / MyBatis 경계와 DB 중립

> **다섯 벤더에서 같은 소스가 돈다**(PostgreSQL · Oracle · MSSQL · MySQL · MariaDB).
> 벤더 차이는 *나중에 고치는 버그* 가 아니라 **그 벤더로 배포한 뒤에야 나타나는 버그**다 —
> 개발용 PostgreSQL 은 다 받아준다.

## 1. 셋의 경계

> **MyBatis 는 아직 클래스패스에 없다**(`libs.versions.toml` 에 좌표가 없다).
> **쓰지 않기로 한 것이 아니라 아직 쓸 일이 없는 것**이며, 아래 규칙은 **들여오는 날의 계약**이다.
> 자리를 미리 정해 두지 않으면 처음 쓰는 사람이 동적 조건까지 그리로 가져간다.

세 가지의 경계를 **명문화하지 않으면 반드시 뒤섞인다.**

| 상황 | 사용 |
|---|---|
| 단건 조회 · 저장 · 수정 · 삭제 | **JPA (Spring Data)** — 기본값 |
| 동적 조건 목록 조회 · 조인 · 프로젝션 | **QueryDSL** — 검색 화면의 표준 |
| 통계·집계 · 다중 조인 리포트 · KPI · 벤더 튜닝 | **MyBatis** — 예외적 사용. **아직 없다**(§1-1) |
| 대량 INSERT/UPDATE | JDBC Batch 또는 MyBatis. **JPA 반복 저장 금지** |

> **MyBatis 를 *예외적 사용* 으로 좁힌 것은 성능 취향이 아니라 안전 장치다.**
> 동적 조건을 MyBatis 로 받으면 **조인 별칭이 XML 에만 존재**해 자바의 화이트리스트와
> **기계로 묶을 수 없다** — 별칭을 개명하면 컴파일도 기동도 통과하고 **그 조건을 쓰는
> 화면에서만 런타임에 깨진다.** QueryDSL 은 조인이 스펙 안에 있어 그 문제가 없다.

### 규칙

1. **쓰기(Command)는 JPA 만.** MyBatis 는 원칙적으로 읽기 전용 — 같은 트랜잭션에서
   MyBatis 로 쓰면 JPA 1차 캐시와 어긋난다.
2. 부득이하면 `EntityManager.flush()` 후 실행하고 **주석으로 사유를 남긴다.**
3. **MyBatis XML 의 `${}` 는 식별자(컬럼·정렬 방향)에만.** 값은 언제나 `#{}` 다.
   식별자에 넣는 것도 **화이트리스트(`*Spec`)가 돌려준 소스 상수**여야 하며 **요청에서 온
   문자열은 어떤 경로로도 SQL 에 들어가지 않는다.** 전면 금지가 아닌 이유는 **JDBC 가
   식별자를 바인딩하지 못하기 때문**이다 — `?` 는 값 자리다. 공통 조각
   `common-search.xml` 하나만 예외이며, **화면 XML 에 `${}` 가 나타나면 그것은 사고다.**
4. QueryDSL `Q` 클래스는 `build/generated` 에 생성. **VCS 미포함.**

### 1-1. MyBatis 를 들여오는 날

**언제** — 업무 도메인이 붙어 **통계·KPI·다중 조인 리포트**가 요구될 때다. 기본 기능만 있는
지금은 QueryDSL 로 충분하고, **쓰지 않는 의존을 미리 넣지 않는다**(`SYS_USER_PWD_RESET` 을
미리 만들지 않은 것과 같은 규약이다 — 쓰지 않는 것은 *됐다* 로 보이지만 한 번도 돌아 본 적이 없다).

**무엇을** — `libs.versions.toml` 에 버전을 적고 `sources/backend/domain` 에 스타터를 더한다.
Spring Boot 4 는 라이브러리만으로 자동설정이 붙지 않으므로 **반드시 스타터**다([stack.md](../stack.md)).

**들어오는 순간 지켜야 하는 것** — 위 규칙 넷과 §6 의 `databaseIdProvider`. 특히
**동적 조건은 따라오지 않는다**: 그 경계가 무너지면 조인 별칭이 XML 에만 남아
화이트리스트와 기계로 묶이지 않는다(위 인용).

## 2. 타입 — 표 안에서만 고른다

| 논리 타입 | Java | Liquibase |
|---|---|---|
| 식별자 | `Long` | `BIGINT` |
| 코드·짧은 문자 | `String` | `VARCHAR(n)` |
| **긴 텍스트** | `String` + **매핑 필수**(§2.1) | **`NCLOB`** |
| 불리언 | `Boolean` + `YnConverter` | `CHAR(1)` |
| 정수 | `Integer` | `INT` |
| 금액·소수 | `BigDecimal` | `DECIMAL(p,s)` |
| 일시 | `Instant` | `TIMESTAMP WITH TIME ZONE` |
| 날짜 | `LocalDate` | `DATE` |
| 바이너리 | `byte[]` | `BLOB` |

**금지**: `JSON`/`JSONB`(벤더 편차 최대 — `CLOB` + 애플리케이션 직렬화) · 배열(PostgreSQL
전용) · `ENUM`(`VARCHAR` + `@Enumerated(STRING)`) · 네이티브 `UUID`(**`CHAR(36)` 로 통일**) ·
공간/지리 타입.

### 2.1 긴 텍스트는 `@Lob` 만으로 이식되지 않는다

`@Lob String` 은 **5개 벤더 중 3곳에서 쓸 수 없는 타입**이 된다 — MySQL 에서는
`tinytext`(255B)가 되어 전문이 들어가지 않는다.

```java
@Nationalized
@JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
private String parameters;
```

### 2.2 `CHAR` 는 Y/N 과 UUID 둘뿐이다

컨버터 없는 **순수 `String`** 필드에 `CHAR(n)` 컬럼을 주면 Hibernate 는 `VARCHAR(n)` 을
기대해 **기동 검증이 실패한다.** 고치는 길이 둘인데(**엔티티에 `@JdbcTypeCode(CHAR)`** /
**컬럼을 `VARCHAR` 로**) — **컬럼을 바꾼다.** 애너테이션 쪽은 *컬럼마다 붙여야 하는 자리*
가 하나 느는 길이고, 벤더별 `CHAR` 패딩 차이(Oracle 은 공백을 채우고 MySQL 은 읽을 때
떼어낸다)를 지고 갈 이유도 없다.

## 3. Y/N 플래그

불리언 성격 컬럼은 `CHAR(1)` `'Y'`/`'N'`. JPA 는 `YnConverter`, MyBatis 는 문자 비교라 안전하다.

> **컨버터의 DB 쪽 타입은 `Character` 다 — `String` 이 아니다.**
> `AttributeConverter<Boolean, String>` 이면 Hibernate 가 `VARCHAR(1)` 로 잡고 changelog 의
> `CHAR(1)` 과 어긋나 **기동이 실패한다.** `Character` 로 두면 `CHAR` 로 잡히므로
> **컬럼마다 `@JdbcTypeCode(CHAR)` 를 붙일 필요가 없다** — 빠뜨릴 자리를 아예 없앤다.

> **`autoApply = true` 는 쓰지 않는다.** 켜면 앞으로 만들 모든 엔티티의 모든 `Boolean` 이
> 조용히 `CHAR(1)` 이 되고, 업무 테이블이 진짜 `BOOLEAN` 을 쓰려 할 때 끄는 방법이 애매해진다.

## 4. 식별자 — `TABLE` 전략

| 방식 | 판정 |
|---|---|
| `IDENTITY` | ✗ Oracle 12c 미만 미지원 · JPA 배치 INSERT 불가 |
| `AUTO` | ✗ 방언마다 다른 전략 선택 → 스키마 불일치 |
| `SEQUENCE` | ✗ **MySQL 에서 Liquibase 가 `createSequence` 를 조용히 건너뛴다** |
| **`TABLE`** | **✓ 채택** — 생성기 테이블을 명시하므로 다섯 벤더가 같은 객체를 쓴다 |

## 5. `IN` 목록 — 쪼개서 보낸다

**재는 자가 벤더마다 다르다.** Oracle 은 **표현식 1000개**(`ORA-01795`), SQL Server 는
**바인드 2100개**, MySQL 은 패킷 크기, PostgreSQL 은 바인드 65535개.

**가장 낮은 것에 맞춘다 — `InChunks.LIMIT = 900`.** 1000 이 아닌 것이 요점이다: 1000 은
오라클의 한계와 *같은* 값이라 여유가 없고, 조건이 하나 더 붙으면 그 자리에서 깨진다.
`PageSpec.MAX_SIZE` 가 1000 이라 **페이지 하나 분량이 정확히 경계 위**에 있었다.

| 어디 | 방법 | 왜 |
|---|---|---|
| **조건절 안** | `IN` 을 나눠 **`OR` 로 잇는다** | 한 `WHERE` 에 남아야 **페이징이 성립한다** |
| **그 자체가 목적인 질의** | **반복 질의** 후 결과를 합친다 | 순서가 결과에 관여하지 않는다 |

> **JPQL 에는 목록이 보이지 않는다.** 자리표 하나(`?1`)로 찍히고 **드라이버로 나갈 때**
> 펼쳐진다 — 한계도 거기서 걸린다. 테스트가 셀 수 있는 것은 *조각이 몇 개인가* 뿐이다.

## 6. 그 밖의 벤더 규칙

- **업무 시각을 SQL로 구하지 않는다** — `SYSDATE`·`NOW()`·`GETDATE()` 금지. 명시된 분산 세션 DB 시각 계약의 한정 예외는 [time.md](./time.md)를 따른다.
  애플리케이션이 주입한다.
- **`ORDER BY` 에 PK tie-breaker 를 붙인다** — 없으면 페이지 경계에서 행이 새거나 겹친다.
- MyBatis 쿼리는 **`databaseIdProvider` 로 벤더 분기 가능**하게 쓴다.
- 테이블명 **30자 이내**, 공통 테이블은 `SYS_` 접두, 예약어 회피.
- **이미 적재된 changeset 은 고치지 않고 새 파일을 더한다** — 고치면 남의 로컬이
  checksum 으로 죽는다(AGENTS.md §5-8).

## 7. 동시 갱신 — **어떤 락을 쓸 것인가**

> **`@CustomLock` 은 배치 전용이 아니다.** 일반 서비스에도 필요하다.
> 다만 **더 안전한 수단이 있을 때는 그쪽을 먼저 본다.**

### 7.1 기본은 비관적 락

집계 상태를 파생 계산하는 자리 — *주문 10건 중 5건 확정이면 부분완료, 10건이면 완료* —
읽고·계산하고·쓰는 사이에 다른 세션이 끼어들면 **둘 다 부분완료를 쓴다.**

**원인은 JPA 1차 캐시가 아니라 `READ COMMITTED` + read-modify-write 다.**
순수 JDBC 로 짜도 똑같이 깨진다 — 1차 캐시는 **간극을 넓힐 뿐 만들지 않는다.**
따라서 해법은 *JPA 를 피한다* 가 아니라 **DB 에서 직렬화한다** 이다.

집계 루트(주문 헤더)가 있으면 **그 행을 잠근다** — `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

| | 비관적 락 | `@CustomLock` |
|---|---|---|
| 해제 시점 | **커밋/롤백 시 DB 가 자동** | 코드가 책임. **누수 가능** |
| 커밋 전 노출 창 | **0** — 해제가 곧 커밋 | flush+commit 구간(§7.3) |
| 만료·정리 배치 | 불필요 | **필요** |
| 순환 대기 | **DB 가 데드락 감지** → 즉시 한쪽 종료 | 감지 못 함 → **만료까지 매달린다** |
| AOP 순서 규약 | 불필요 | **필수** |

**생명주기가 트랜잭션과 정확히 일치하는 것**이 비관적 락의 본질적 우위다.

### 7.2 선택 표

| 상황 | 수단 |
|---|---|
| 집계 상태 파생 계산, **부모 행 존재** | **비관적 락** |
| 총량 제한, **잠글 행 있음** | **비관적 락**(쿼터 행) |
| 총량 제한, **잠글 행 없음** — 아직 없는 행을 세는 팬텀 | `@CustomLock` |
| **구조 불변식이 여러 행에 걸침**(트리 순환 금지) — 각 행을 잠가도 write-skew 로 깨진다 | `@CustomLock` · **`WAIT` 필수** |
| **트랜잭션 경계를 넘음**(비동기로 분리) | `@CustomLock` — 비관적 락은 원리적으로 불가능 |
| 단일 행 갱신 충돌 | `@Version` + **자동 재시도** |
| 다중 노드 스케줄 배치 | 스케줄러 래퍼([batch.md](./batch.md)) |
| 단순 조회 | **없음 — 락 금지** |

**잠글 대상을 만들 수 있으면 만들어서 비관적 락을 건다.** 쿼터·카운터 행이 그렇다 —
**정말 잠글 대상이 없을 때만** `@CustomLock` 이다.

`@Version` 은 **자동 재시도를 붙이지 않으면** 한쪽 사용자에게 반복 오류로 나타난다.
반대로 재시도는 업무 로직을 다시 돌리므로 **부수효과가 있는 흐름에는 쓸 수 없다.**

### 7.3 `@CustomLock` 은 트랜잭션을 감싸야 한다

```
정상                          결함
  lock                          tx begin
  tx begin                        lock
    work                          work
  commit                          unlock   ← 여기서 풀린다
  unlock                        commit     ← 실제 반영은 여기
```

**해제가 커밋보다 먼저 일어나면 방어에 틈이 생긴다.** JPA 는 커밋 시점에 flush 하므로
노출 구간은 **flush + commit 시간뿐**이고, 작업이 200ms · 커밋이 5ms 면 2% 남짓이다.

> **락이 무력화되는 것은 아니다 — 넓은 경합이 좁은 경합이 될 뿐이다.**
> 그러나 **좁다고 안전하지는 않다.** 그래서 AOP 순서를 규약으로 두고 테스트가 지킨다.

### 7.4 MQ 로 직렬화하지 않는 이유

큐로 넘기면 확실히 직렬화되지만 **비동기가 되고 즉시 피드백을 잃는다** — 폴링을 만들어야
하고 사용자는 결과를 바로 못 본다. MQ 는 **고경합 처리량**을 위한 도구이고, 백오피스는
경합이 낮고 **즉시성의 가치가 크다.** `WAIT` 정책이면 *오류 대신 잠깐 대기 후 성공* 이
되는데 그것은 **비관적 락이 기본으로 주는 의미론과 같다.**

## 새 테이블·쿼리 체크리스트

- [ ] 타입이 §2 표 안에 있는가
- [ ] 불리언 성격 컬럼은 `CHAR(1)` Y/N 인가
- [ ] ID 는 `TABLE` 전략인가
- [ ] changelog 가 벤더 중립 명령인가
- [ ] 금지 함수를 쓰지 않았는가
- [ ] `ORDER BY` 에 PK tie-breaker 가 있는가
- [ ] `IN` 목록이 900 을 넘을 수 있는가

## 관련

- [search-spec.md](./search-spec.md) — 화이트리스트와 빌더
- [packages.md](./packages.md) — 트랜잭션 경계

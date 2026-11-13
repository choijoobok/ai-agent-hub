---
id: LLM-FWK-BE-009
title: "횡단 관심사 — 필터 · 인터셉터 · 순서 · 비동기 · 중복 요청"
category: standard/backend
type: operation
class: policy
created: 2026-09-03
updated: 2026-09-12
status: active
sources: ["sources/backend/bootstrap/src/main/java/com/aiagent/bootstrap/config/InterceptorConfig.java", "sources/backend/api/", "sources/backend/modules/system/", "sources/backend/bootstrap/src/main/resources/application.yml"]
tier: 2
tags: [backend, filter, interceptor, aop, order, async, virtual-thread, dedup]
---

# 횡단 관심사 — 필터 · 인터셉터 · 순서 · 비동기 · 중복 요청

> **순서가 규약이다.** 두 줄의 자리를 바꾸면 **403 이 조용히 이력에서 사라진다.**
> 그래서 이 파일에는 *무엇이 있는가* 보다 *왜 그 순서인가* 가 더 중요하다.

## 1. 파이프라인

```
요청
  ↓
[RequestIdMdcFilter]        클라이언트 발급 X-Request-Id 를 이어받아 MDC 에 심는다
  ↓
[RequestCachingFilter]      ContentCachingRequestWrapper — body 재독 가능
  ↓
Spring Security 필터 체인    stateless 헤더 · 인증 포트 외 WAS 세션 금지
  ↓
[RequestDedupInterceptor]   ← 예정. 자리는 Security 다음, 인가 앞 (§6)
  ↓
[administratorRequestContext]  인증 경계 종료 뒤 사용 이력 한 번 기록
  ↓
[SessionV2Filter]           현재 계정·메뉴×액션 보호 · CSRF · 커밋 뒤 세션 발행
  ↓
Controller
  ↓
[@RestControllerAdvice]     예외 → 봉투 + 에러 로그
  ↓
afterCompletion             SYS_LO_ACCESS — 전 요청 1행
```

## 2. 등록 순서가 규약인 이유

**`preHandle` 이 거부해도, 이미 `true` 를 반환한 인터셉터의 `afterCompletion` 은 호출된다.**

그래서 사용 이력 인터셉터를 **앞에** 두어야 403 이 이력에 남는다.
**뒤에 두면 인가가 막은 요청이 통째로 사라진다** — 그리고 *"권한 없는 접근 시도"* 는
사용 이력이 가장 자주 답해야 하는 질문이다.

순서가 [`InterceptorConfig`](../../../sources/backend/bootstrap/src/main/java/com/aiagent/bootstrap/config/InterceptorConfig.java)
**한 파일에서만 표현되므로** `InterceptorRegistrationOrderTest` 가 지킨다.

## 3. `addPathPatterns("/api/**")` 로 좁히지 않는다

**좁히면 패턴에 걸리지 않는 새 경로가 판정 없이 지나간다.**
인터셉터가 **전부 받고** `ApiLayer` 가 분류하는 편이 **fail-closed** 다
([security.md §1](./security.md) 의 *세 프리픽스 밖은 무조건 403* 과 같은 방향).

## 4. body 를 두 번 읽기

`HttpServletRequest` 의 body 는 **한 번만** 읽을 수 있다. 컨트롤러가 읽은 뒤에도
인터셉터·예외 핸들러가 같은 내용을 보려면 **필터에서 미리 감싸야** 한다 —
`RequestCachingFilter` 가 그 일만 한다.

| 항목 | 값 |
|---|---|
| 적용 URL | `/api/*` — Ajax API 만 |
| 제외 Content-Type | `multipart/` — 업로드 바이너리를 버퍼링하면 OOM |
| 캐시 상한 | **1MB — 문자가 아니라 UTF-8 바이트로 잰다** |
| 설정 | `app.request-cache.*` |

> **상한은 바이트다 — 문자로 재면 최대 3배가 샌다.** 실측:
> `chars = 1,048,576 → utf8Bytes = 1,359,272` (한글+ASCII 혼합, 배율 1.296).
> 전문이 **전부 한글이면 3배**다. 문자로 잡으면 *"1MB 제한"* 이 실제로 3MB 를 흘려보내고
> 용량 산정이 어긋나는데, `PARAMETERS` 는 `nclob` 이라 **넘친 사실조차 드러나지 않는다.**
> 절단은 **UTF-8 바이트 경계에서** 한다 — 문자 중간에서 자르면 깨진 바이트가 남는다.

## 5. 관심사와 자리

| 관심사 | 구현 | 자리 |
|---|---|---|
| 추적 ID(MDC) | `RequestIdMdcFilter` | **최우선** |
| 요청 body 캐싱 | `RequestCachingFilter` | 추적 ID 다음 |
| 인증 | `SessionV2Filter` + `AdminAuthorityGuard` | 현재 계정·권한과 업무가 같은 DB 트랜잭션 |
| **중복 요청 방어** | `RequestDedupInterceptor` — **아직 없다**(§6) | Security 다음, 인가 앞 |
| **사용 이력** | 바깥 `administratorRequestContext` 필터가 기록기 호출 | 인증 필터 종료 뒤401/403 포함 |
| **인가** | `AdminAuthorityGuard` | `AuthorizationInterceptor`는 현재 권한 버전 응답만 연결 |
| 에러 로그 | `@RestControllerAdvice` + 비동기 저장 | 예외 발생 시 |
| 개인정보 접근 이력 | PII 응답 어드바이스 | 복호화 시점 |

---

## 6. 중복 요청 방어 — `at-most-once`

> **구현이 아직 없다.** 계약은 확정돼 있고 **기본은 꺼짐**이다.

### 6.1 무엇을 막나 — 구분이 먼저다

프록시·로드밸런서가 **응답을 못 받아 같은 요청을 다시 보내는 것**을 막는다.
사용자가 다시 누른 것은 **새 id 로 오므로 막히지 않는다** — 더블클릭도 대상이 아니다.

**같은 `request-id` 는 최대 한 번만 실행된다.** 첫 시도가 실패해도 같은 id 의 재전송은
재시도로 허용하지 않는다.

### 6.2 키 채널을 이중화한다 — 헤더 하나로는 실전에서 유실된다

| 유실 원인 | 사례 |
|---|---|
| nginx 언더스코어 헤더 폐기 | `underscores_in_headers off` 가 기본 |
| 게이트웨이 헤더 덮어쓰기 | 자체 request-id 로 교체 |
| 헤더 화이트리스트 정책 | 미등록 헤더 제거 |

그래서 **같은 값을 헤더(`X-Request-Id`)와 쿼리(`_rid`) 양쪽에** 싣는다.

- 둘 중 **하나라도** 저장소에 있으면 **중복**
- 두 값이 서로 다르면 **중간 레이어의 변조 신호** → WARN 로그
- 둘 다 없거나 형식이 비정상이면 → **통과**(외부 시스템 직접 호출 등)
- 형식은 `^[A-Za-z0-9-]{8,64}$` — **MDC 필터와 같은 정규식을 공유한다.**
  통과 못한 값은 없는 것으로 본다(로그 인젝션 · 키 오염 방지)

### 6.3 추적 ID 와 같은 값을 쓴다 — 그래야 진단이 된다

별도의 멱등 키를 만들지 않는다. **클라이언트가 발급하고 서버가 이어받는다.**

| 로그에서 보이는 것 | 해석 |
|---|---|
| 같은 `requestId` 가 두 번 | **인프라 재전송** |
| 다른 `requestId` | 사용자의 실제 재요청 |

**서버가 생성하는 추적 ID 로는 이 구분을 할 수 없다** — 재전송마다 값이 달라진다.
해석 우선순위는 `헤더 → 쿼리 → 서버 생성 UUID`.

### 6.4 저장소는 선택형이고 **폴백이 `local` 이 아니다**

| store | 획득 | 정리 |
|---|---|---|
| `auto`(기본) | Redis 있으면 Redis, 없으면 **JDBC** | — |
| `redis` | `SET key 1 NX EX ttl` — 원자적 | native TTL |
| `jdbc` | PK **UNIQUE INSERT** → 위반이면 중복 | **정리 배치 필요** |
| `local` | Caffeine `putIfAbsent` | 자동 만료 |

**`local` 은 명시했을 때만 고른다.** 다중화 + 비스티키 환경에서 로컬 캐시를 쓰면 다른
노드로 간 복제본을 못 잡아 **방어가 조용히 무력화**된다 — 중복 방어는 정확성이 목적이라
자동 폴백 대상이 아니다.

`tryAcquire()` 는 *검사 후 기록* 이 아니라 **원자적 선점**이어야 한다. 그래야 첫 요청이
끝나기 전에 도착한 두 번째도 하나만 성공한다.

### 6.5 적용 범위와 TTL

| 항목 | 값 |
|---|---|
| 메서드 | `POST` `PUT` `PATCH` `DELETE` — **상태 변경만**. GET 은 통과 |
| 경로 | `/api/**` |
| 제외 | `/api/public/**` · `/api/common/**` — **프리픽스 단위**(인증·진행률·파일이 전부 이 둘 안에 있다) |
| TTL 기본 | 600초 |

> **TTL 은 앞단 프록시의 재전송 타임아웃보다 길어야 한다.** 짧으면 재전송이 이미 만료된
> 키를 만나 **중복 실행되고 방어가 무의미해진다.** 길면 정상 재시도까지 막지만
> `at-most-once` 를 택한 이상 **그쪽이 안전하다.** 기본 600초는 일반적인 프록시
> 타임아웃(60~300초)의 두 배 이상을 확보한 값이다.

`enabled: false` 일 때 **`@ConditionalOnProperty` 로 빈을 아예 등록하지 않는다** —
인터셉터가 없으므로 동작·성능 영향이 0 이다.

### 6.6 중복이면 200 + 전용 코드로 **조용히** 끝낸다

```
중복 판정 → HTTP 200 + { success: false, code: "err.duplicate-request" }
              ↓
        프론트 인터셉터가 삼킨다 (토스트·모달 없음)
```

에러 토스트를 띄우면 사용자가 *"다른 작업자가 처리했나"* 로 오해한다.
**레이스 컨디션 에러와 구분**하려고 전용 코드를 둔다.

### 6.7 알고 쓰는 한계

| 한계 | 내용 |
|---|---|
| 중간 레이어가 **두 채널을 모두 지우면 무력** | 이중화는 확률을 낮췄을 뿐이다 |
| **성공 피드백 유실** | 프록시가 첫 연결을 버리고 재전송 응답만 중계하면 브라우저는 `err.duplicate-request` 만 받는다 — **데이터는 저장됐는데 성공 토스트가 없다.** 사용자가 재조회하는 쪽을 택했다 |
| **응답 재생을 하지 않는다** | 응답 본문 저장·크기 상한·요청 해시 대조가 전부 필요해 복잡도가 크게 오른다. 목록 응답이 수 MB 인 백오피스에서 비용 대비 이득이 낮다 |
| **fail-open** | 저장소 장애 시 **막지 않고 통과**시킨다. 이것은 **보조 안전장치**이지 정합성의 최후 방어선이 아니다 — 진짜 정합성은 **업무 규칙의 유니크 제약**이 지킨다 |

---

## 7. 비동기 — Virtual Thread 와 컨텍스트 전파

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

이 한 줄로 **Tomcat 요청 처리 · `@Async` · `@Scheduled`** 가 전부 Virtual Thread 로 돈다.
JDK 25 이므로 `synchronized` 핀닝 제약이 없다(JEP 491).

> **"핀닝이 사라졌다" 가 아니라 "`synchronized` 로 인한 핀닝이 사라졌다"** 다.
> 남은 핀닝은 JNI 프레임과 클래스 초기화자이며, **병목은 사라진 게 아니라 커넥션 풀 등
> 외부 자원으로 옮겨 갔다**(§8).

### 7.1 무엇을 전파하고 무엇을 전파하면 안 되는가 — **이 구분이 핵심이다**

| ThreadLocal | 전파 | 사유 |
|---|---|---|
| `SecurityContextHolder` | ✅ **필수** | 비동기 작업에서 사용자 식별·권한 검사 |
| **MDC**(`requestId` · `userId`) | ✅ **필수** | 없으면 **비동기 로그가 요청과 끊긴다** |
| `LocaleContextHolder` | ✅ | 다국어 메시지 |
| **시스템 설정 스냅샷** | ✅ **복사** | 한 덩어리 일 안에서 값이 바뀌면 안 된다([knowledge/sys/config.md](../../knowledge/sys/config.md)) |
| Micrometer `Observation` | ⬜ 향후 | 채택 구현은 MDC·Locale 만 옮긴다 |
| **`TransactionSynchronizationManager`** | ❌ **절대 금지** | §7.2 |
| `RequestContextHolder` | ⚠️ **원칙 금지** | §7.3 |

### 7.2 트랜잭션은 절대 전파하지 않는다

거기에는 현재 스레드의 `EntityManager` 와 JDBC `Connection` 이 묶여 있다.

- **Hibernate `Session` 은 thread-safe 하지 않다.** 두 스레드가 같은 영속성 컨텍스트를
  만지면 1차 캐시가 깨지고 **데이터가 손상된다.**
- `Connection` 을 공유하면 커밋·롤백 경계가 뒤엉킨다.

**비동기 작업은 자기 트랜잭션을 새로 시작한다. 예외 없다.**

### 7.3 요청 스코프는 복사해서 넘긴다

`RequestContextHolder` 를 전파하면 **요청이 먼저 끝났을 때 무효한 객체**를 참조하게 된다.
필요한 값은 **작업 제출 시점에 꺼내 파라미터로** 넘긴다.

> **금지되는 것은 살아 있는 객체이지 값이 아니다.** 설정 스냅샷은 **불변 값 묶음**이라
> 그대로 복사해 넘기며, 그래야 요청과 그 요청이 띄운 비동기 작업이 **같은 설정을 본다**.
> `@Scheduled` 는 물려받을 부모가 없어 **자기 스냅샷을 새로 뜬다.**
> **오래 도는 작업이 낡은 값에 묶이는 것은 의도다** — 반영하려면 작업을 다시 돌린다.

### 7.4 `TaskDecorator` 하나로 일원화한다

`DelegatingSecurityContextAsyncTaskExecutor` 만으로는 **`SecurityContext` 밖에 못 옮긴다.**
MDC·Locale·설정 스냅샷을 함께 옮기려면 `TaskDecorator` 가 필요하고, 그것이 **한 자리**에
있어야 새 ThreadLocal 이 생겼을 때 고칠 곳이 하나다.

**`@Async` 풀과 진행률 작업 풀을 분리한다** — 오래 걸리는 작업이 일반 `@Async` 를
잠식하지 않게 한다.

## 8. 동시성 제한 — Virtual Thread 의 함정

> **"Virtual Thread 는 싸니까 마음껏 만들어도 된다" 는 틀렸다.**

수만 개를 만들 수 있지만 **DB 커넥션은 유한**하다(HikariCP 기본 10).

```
1만 건 일괄 처리 → VT 1만 개 → 전부 커넥션 요청
   → 커넥션 대기 타임아웃 → 작업 실패
   → 요청 처리 스레드도 VT 라 같은 풀을 쓴다 → 전체 서비스 영향
```

**작업 유형별로 상한을 둔다**(`Semaphore` 게이트 또는 유형별 executor).

| 작업 성격 | 동시성 |
|---|---|
| DB 위주 | **커넥션 풀 크기 이하** |
| 외부 API 호출 | 상대 시스템의 허용 한도 |
| CPU 위주 | **Virtual Thread 이점이 없다.** 플랫폼 스레드 풀(코어 수) — VT 를 쓰면 컨텍스트 스위칭만 는다 |

## 9. 새 횡단 관심사를 더할 때

1. **필터인가 인터셉터인가** — 서블릿 레벨(요청 감싸기·MDC)은 필터, 핸들러를 알아야 하면
   인터셉터.
2. **어느 자리인가** — *인가가 막은 요청도 이것이 봐야 하는가* 를 묻는다. 그렇다면
   인가보다 앞이다.
3. **순서가 파일 하나에만 있으면 테스트로 지킨다** — 주석은 두 줄을 맞바꾸는 것을 막지 못한다.
4. **경로 패턴으로 좁히지 않는다** — 전부 받고 안에서 분류한다.
5. **새 ThreadLocal 을 만들었으면 `TaskDecorator` 에 넣을지 정한다**(§7.1) — 안 넣으면
   비동기 경계에서 조용히 사라진다.

## 관련

- [security.md](./security.md) — 인가 인터셉터가 하는 일
- [logging.md](./logging.md) — 사용 이력 · 에러 로그의 저장 규약
- [progress.md](./progress.md) — 오래 걸리는 작업의 진행률
- · [knowledge/sys/config.md](../../knowledge/sys/config.md)

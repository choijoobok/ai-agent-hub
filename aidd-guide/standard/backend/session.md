---
id: LLM-FWK-BE-007
title: "세션 — 스토어 선택 · 담는 것 · 동시 접속"
category: standard/backend
type: operation
class: policy
created: 2026-05-04
updated: 2026-11-11
status: active
sources: ["sources/backend/bootstrap/src/main/resources/application.yml", "aidd-guide/standard/backend/security.md"]
tier: 2
tags: [backend, session, spring-session, redis, jdbc]
---

# 세션 — 스토어 선택 · 담는 것 · 동시 접속

> **세션을 외부화하되 Redis 를 필수 인프라로 강제하지 않는다.**
> 고객사마다 인프라가 다르고, 폐쇄망에서 Redis 를 세우지 못하는 곳이 실재한다.

## 1. 저장소 선택과 현재 구현

WAS 메모리 세션은 사용하지 않는다. JDBC 기본·Redis 선택은 같은 제품 artifact의 명시 기동 설정으로 정한다. 선택한 저장소 장애 때 다른 저장소로 자동 폴백하지 않는다. 라이브러리 존재만으로 provider를 선택하지 않고 자동 기본 세션 필터와 영역 조립의 중복도 기동 검사한다.

현재 공통 v1/JDBC 조립은 SLICE-307에서 검증했지만 실제 system 소비 전환은 후속이다. Redis/JDBC 설정 교체의 v2 저장/발급 상세는 integration 영향 G3에서 동결했고 실제 구현/소비 검증은 남아 있다. CR-038 API 경로 전환은 이 저장/회수 프로토콜을 바꾸지 않으며 별도 경로 영향 검토를 따른다. 프로젝트의 설정 키·DB 원장·상태/기한·전환 절차는 `documents/spec/50-api/020-auth-store-contracts.yaml`이 정본이며, 아직 제품 설정만으로 v2 Redis가 동작한다고 주장하지 않는다.

## 2. 기술 조립과 원자성

공개 포트/필터는 JDBC·Redis 구체 저장소를 받지 않는다. 순수 Java 수락/조회/관리 참조와 기술 provider 조립을 분리한다. DB 수락·회수·현재 기한/epoch와 선택 store payload를 모두 검사하고 세션 비밀/payload를 DB 원장에 복제하지 않는다.

JDBC는 업무/원장/세션을 같은 DB REQUIRED의 한 물리 커밋에 참여시킨다. Redis는 DB 확정 뒤 잠금 밖에서 payload를 발행하고 현재 PENDING 세대를 다시 검사해 ACTIVE를 확정한 뒤 쿠키를 발급한다. DB 확정 뒤 발행 실패는 새 자격 재로그인으로 복구하며 원 명령을 자동 재실행하지 않는다. 종료된 admission을 후처리에서 재사용하지 않는다.

## 3. 세션에 담는 것 — 계산 **결과**만

| 담는다 | 담지 않는다 |
|---|---|
| principal (사용자ID · 로그인ID **최소 필드**) | 계산 **입력** — 역할 목록 · 개인 예외 · 유효기간 |
| **유효 권한 계산 결과** | **메뉴 트리** — 로그인 후 프론트 메모리로 1회 |
| 표시/언어 스냅샷 | 권한 허용 근거로 재사용할 과거 계정 상태 |

- **`serialVersionUID` 를 고정한다.** 무중단 배포 시 역직렬화 실패를 막는다.
- **유효 권한이 세션에 있는 이유**는 [caching.md](./caching.md) §1 — 매 요청 필요하지만
  **낡으면 보안 결함**이라 TTL 캐시로 둘 수 없다.
- 계산 입력을 담지 않는 이유: 담으면 **같은 사실이 두 곳**에 생기고, 관리자가 역할을
  회수했을 때 **무엇을 지워야 하는지가 갈린다.**
- 권한 결과와 **계산 전에 관측한 무효화 표식**을 한 속성에 담는다. 변경마다 고유 표식을
  쓰고, 재계산은 표식을 삭제하지 않는다. 두 표식이 다르거나 업무일자가 바뀌면 재계산한다.
  로그인 후 첫 보호 요청도 세션 색인 등록 전 회수를 확인하기 위해 재계산한다.
- Spring Session은 **`ON_SET_ATTRIBUTE`**로 저장한다. `ON_GET_ATTRIBUTE`·`ALWAYS`는
  낡은 요청이 읽은 표식까지 덮어쓸 수 있으므로 사용하지 않는다. JDBC 설정은
  `spring.session.jdbc.save-mode: on-set-attribute`로 명시한다. Redis 전환 시에도 같은 저장 모드를 유지한다.
- 권한 속성의 직렬화 형식을 바꿀 때는 노드를 일괄 전환하고 기존 세션을 종료해 재로그인한다.
  구 코드가 새 형식을 읽지 못하면 혼합 운영하지 않으며 롤백할 때도 세션을 종료한다.

## 4. 세션 중 재검사 — 현재 소유 guard가 한다

세션이 유지되는 동안 상태가 바뀔 수 있다(관리자가 계정 비활성화). 그래서 **계정 상태와
유효 권한을 매 요청 다시 본다**. 보호 트랜잭션의 소유 guard에서 현재 원천을 확인하며,
세션의 계산 결과/dirty 표식은 표시 캐시일 뿐 현재 저장 허용의 근거가 아니다.
체인 전체는 [security.md §5](./security.md).

## 5. 만료 처리

| 상황 | 응답 | 프론트 |
|---|---|---|
| 세션 없음·만료 | **401** | **재인증 모달** — 로그아웃하지 않는다 |
| 비밀번호 만료 | 제한 세션 → 변경 API 외 **403** | 비밀번호 변경 화면으로 유도 |

**401 에서 로그아웃하지 않는 것이 규칙이다** — 작성 중이던 입력이 사라지면 사용자가
같은 일을 두 번 한다.

## 6. 다른 세션 조회와 선택 종료

소유 서비스는 전용 SessionInspectionPort로 현재 권한과 대상 사용자를 검사한 뒤 목록을 얻는다. raw SessionRepository를 전역 주입하거나 요청 wrapper 밖에서 독립 saveContext를 호출하지 않는다. 주체 인덱스는 영역별로 분리하며 JDBC 물리 후보 조회와 Redis namespace 해석은 provider 안에서만 수행한다.

일반 API에는 인증 쿠키/CSRF/JDBC row ID/Redis key를 넣지 않는다. 독립 관리 참조를 현재 세대에 결합하고, 선택 종료 때 현재 권한·대상 사용자·영역·세대를 다시 검사한다. DB 회수가 확정되면 store 삭제 실패나 과거 snapshot 복구로 접속이 부활하지 않는다. 새 접속의 토큰/참조를 이전 접속의 늦은 저장·삭제로 바꾸지 않는다.

## 7. 보장하는 것 — 전환과 검증

설정판의 provider/설치 식별자/epoch는 운영 전환에서만 바꾼다. 노드는 기동·매 수락 때 배포 설정과 현재 판의 일치를 검사한다. 통제된 재시작과 전체 재로그인으로 전환/되돌림하며, JDBC로 되돌려도 과거 JDBC payload를 재수락하지 않는다. v1 바이너리 rollback은 원장 검사가 없으므로 별도 인증 payload 무효화 검증이 필요하다.

양 provider 실제 HTTP·현재 DB 수락·늦은 CAS·독립 장애·복원·설정 혼합·관리 참조와 비밀 출력 반례를 시험한다. 기본 단위시험 통과를 별도 실제 저장소 시험 완료로 세지 않는다. 프로젝트의 G3/G4 승인과 미실행 검증은 project.state.yaml을 따른다.

## 관련

- [security.md](./security.md) — 접근 제어 체인 · 계층 셋
- [caching.md](./caching.md) — 대상마다 다른 캐시 백엔드

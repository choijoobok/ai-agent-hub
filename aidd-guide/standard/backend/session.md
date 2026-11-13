---
id: LLM-FWK-BE-007
title: "세션 — 스토어 선택 · 담는 것 · 동시 접속"
category: standard/backend
type: operation
class: policy
created: 2026-05-04
updated: 2026-11-14
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

공통 v2 조립을 실제 ADMIN 제품이 소비한다. 저장소 설정·DB 수락 원장·기한·전환 절차는
`documents/spec/50-api/020-auth-store-contracts.yaml`, 제품 설치는
`AdminSessionConfiguration`, 실행/검증 근거는 `project.state.yaml`을 따른다. Portal은
소유 구현 전 비활성이며 ADMIN 로그인이 Portal의 접근 권한을 만들지 않는다.

## 2. 기술 조립과 원자성

공개 포트/필터는 JDBC·Redis 구체 저장소를 받지 않는다. 순수 Java 수락/조회/관리 참조와 기술 provider 조립을 분리한다. DB 수락·회수·현재 기한/epoch와 선택 store payload를 모두 검사하고 세션 비밀/payload를 DB 원장에 복제하지 않는다.

JDBC는 업무/원장/세션을 같은 DB REQUIRED의 한 물리 커밋에 참여시킨다. Redis는 DB 확정 뒤 잠금 밖에서 payload를 발행하고 현재 PENDING 세대를 다시 검사해 ACTIVE를 확정한 뒤 쿠키를 발급한다. DB 확정 뒤 발행 실패는 새 자격 재로그인으로 복구하며 원 명령을 자동 재실행하지 않는다. 종료된 admission을 후처리에서 재사용하지 않는다.

## 3. 세션에 담는 것

v2 payload는 영역·주체·인증 수준·발급 세대/epoch·CSRF 비밀과 표시명/언어를 갖는다.
원장에는 현재 수락 상태와 시각만 두며 암호·쿠키·CSRF 원문을 복제하지 않는다.
역할/개인권한과 과거 유효 권한 스냅샷은 현재 허용 근거로 쓰지 않는다.

기본 Spring Session 자동 필터와 WAS HttpSession 접근은 금지한다. 기존 JDBC 부모/속성
테이블은 명시 v2 adapter만 사용하며, 낡은 요청의 갱신은 삭제된 행을 INSERT로 되살리지 않는다.
관리 화면의 sessionId는 인증에 쓸 수 없는 관리 참조다. 원 쿠키·물리 세션키를 응답하지 않는다.

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

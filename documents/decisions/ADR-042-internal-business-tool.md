---
id: ADR-042
title: "내부 서비스 빈 업무 도구와 API 실행 기술자 분리"
status: accepted
date: 2026-11-04
deciders: ["제품 오너(사용자)"]
supersedes: ADR-031
---

## 1. 맥락

사용자는 업무 도구를 Hub 내부 서비스 호출로 한정하고 system 스케줄 배치의 빈 호출 방식과 업무 API 관리 화면을 차용하도록 요청했다. 이어 자바 리플렉션으로 입력·응답 구조를 자동 설정하는 방향을 제시했다. 이 결정은 그 범위의 설계 변경 승인이고 화면 검토·G3·제품 구현 승인은 아니다. 등급은 High다.

현재 소스의 `BatchJobRegistry`는 Spring이 주입한 `Map<String, BatchJob>`을 보관하고 저장·실행 시 존재를 검사한다. `BatchJob.run(BatchJobContext)`라는 고정 진입점을 호출한다. 임의 빈/메서드를 문자열로 실행하는 방식이 아니다. 배치 실행 문맥에는 호출 사용자가 없고 비동기 결과 형식도 업무 도구와 다르므로 레지스트리 패턴을 차용한다.

이전 ADR-031은 API마다 SCR-019의 별도 등록 도구 하나를 요구했다. 내부 전용 화면으로 바꾸면서 이 전제를 유지하면 API 호출 경로가 없어지므로 함께 대체한다. 제품 규칙의 정본은 [REQ-612](../spec/10-requirements/013-reference-operations.yaml)의 internal_invocation·workspace·api_tool_mapping 및 [REQ-608](../spec/10-requirements/012-reference-business.yaml)의 runtime_tool이다.

## 2. 대안

| 대안 | 장점 | 비용·한계 |
|---|---|---|
| API/내부 유형을 기존 도구 화면에 함께 등록 | 기존 ADR-031 구조 유지 | 같은 API를 두 화면에서 등록하고 사용·그룹을 중복 관리해야 한다 |
| 내부 서비스 빈 전용 도구 + API 계약 기반 실행 기술자 | 관리 원천이 명확하고 API와 공통 편집/실행 통제를 재사용 | agent에 두 원천의 어댑터·식별 네임스페이스가 필요하다 |
| 임의 서비스 빈과 메서드를 이름으로 등록 | 기존 빈을 빠르게 연결 | 허용 대상·입출력·사용자 권한 경계가 불명확하고 현재 배치 패턴과도 다르다 |

## 3. 결정

두 번째 대안을 선택한다. 업무 도구는 전용 인터페이스를 구현해 검수된 빈의 고정 진입점만 호출한다. 입력/반환 타입·제네릭·직렬화/검증 애너테이션으로 계약을 분석해 빈 선택 시 입력·응답 필드를 자동 설정한다. 업무 메서드/getter를 실행해 구조나 값을 수집하지 않는다. 동적 타입·해석 불가 구조는 개발자의 명시 계약과 실행 검증이 있어야 사용할 수 있다.

관리자는 7탭에서 가이드·값 확보·코드 의미·개인정보·그룹별 응답 정책을 보완한다. 구조 재조회/빈 변경은 차이를 확인하고 같은 경로의 보충 설정을 보존하며 참조 파손은 보완한다. 전체 저장·HIST와 실행 전 현재 권한·계약 대조를 유지한다.

외부 API 실행 기술자는 API 계약에서 파생한다. 별도 업무 도구 ID·등록·이중 그룹·API 대응 점유는 없다. 기존 미구현 ENT-035는 비저장 논리 기술자로 재정의한다. 지식 API 연결·현재 API 권한/사용·서버 설정·신원·실행 확인은 계속 검사하며 자동 구성 자체는 호출 허가가 아니다.

## 4. 대가와 검증

내부 실행에는 인증 사용자·허용 데이터 범위·승인/감사 근거를 서버가 신뢰 문맥으로 전달해야 한다. 배치의 문맥·큐·결과 형식·취소/트랜잭션을 그대로 사용하지 않는다. 시간 초과는 이미 발생한 효과의 롤백을 보장하지 않으며 결과 불명·자동 재호출 금지를 유지한다.

TC-02120·02121 및 SCR-019의 검수 사례로 현재 사용/구현 무효, 자동 구조·동적 타입, 7탭 입력 보존, 코드/결과 참조, 마스킹·실행 승인 경계를 검증한다. 목업 시험은 실제 리플렉션/호출/저장 구현의 증거가 아니다. agent P3와 admin G3 전에 공개 레지스트리·실행/직렬화 계약 및 논리 자식 구성의 물리를 교차 확인한다.

## 5. 되돌리기

현재 대상은 미구현 설계이므로 데이터 이행 없이 정본·목업을 다시 변경할 수 있다. 구현 후 별도 API 등록 도구를 복원하려면 안정 식별·지식 참조·그룹 정책·실행 감사의 이행이 필요하다. 임의 빈 호출로 범위를 넓히는 것은 이 결정의 단순 옵션이 아니며 새로운 경계 검토 대상이다.

## 6. 근거

- 사용자 2026-11-04 내부 서비스 빈 전용 재설계 및 리플렉션 자동 설정 요청.
- `sources/backend/domain/src/main/java/com/aiagent/domain/batch/BatchJobRegistry.java`, `BatchJob.java`; `sources/backend/domain/src/main/java/com/aiagent/domain/batch/run/BatchRunner.java`.
- [JDK 25 Method](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Method.html): 입력·반환 제네릭/애너테이션 조회. [Spring ResolvableType](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/ResolvableType.html): 구현 타입의 제네릭 해석. 구체 생성기는 후속 상세 설계한다.

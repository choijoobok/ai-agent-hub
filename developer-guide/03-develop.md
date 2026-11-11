# 03. 개발 방법 — 화면 하나를 만드는 순서

> **규칙의 정본은 [`AGENTS.md` §6](../AGENTS.md) 와
> [`aidd-guide/process/07-slice.md`](../aidd-guide/process/07-slice.md) 다.**
> 여기는 *사람이 무엇을 언제 보게 되는가* 를 옮긴 것이다.

## 1. 순서 — 여섯 단계

```
① 요건 정의서 ──→ ② 목업 ──→ ③ 사용자 승인 ──→ ④ 개발 · 테스트 ──→ ⑤ 사용자 직접 테스트 ──→ 완료
      ↑                          │                                          │
      └────── 반려 · 수정 ────────┘                                          │
      └──────────────────── ⑥ 개선 · 보완 요청 ────────────────────────────┘
```

**③ 승인 전에는 AI 가 소스를 건드리지 않는다.** 이것이 이 순서의 유일한 목적이다.

| 단계 | 누가 | 사용자가 하는 일 |
|---|---|---|
| ① 요건 정의서 | AI | (기다린다. 모르는 것을 물으면 답한다) |
| ② 목업 | AI | 〃 |
| **③ 승인** | **사용자** | **두 문서를 함께 보고 승인하거나 반려한다** |
| ④ 개발·테스트 | AI | (기다린다) |
| **⑤ 직접 테스트** | **사용자** | **실제로 써 본다** |
| ⑥ 개선·보완 | 사용자 | 고칠 것을 말한다 → ①로 돌아간다 |

## 2. ①과 ②는 함께 온다 — 승인은 둘을 같이 보는 판단이다

AI 는 `documents/screen/{페이지경로}/` 에 둘을 만들어 함께 낸다.

| 문서 | 무엇을 확인하나 |
|---|---|
| `requirements.md` | **컬럼 · 조회조건 · 버튼 · 검증 · 상태 전이 · 영향 테이블 · API.** *무엇을* 만들지가 여기 있다 |
| `mockup.html` | **배치 · 상태별 화면 · 테스트케이스**(정상·경계·**거부**). *어떻게 보이나* 와 *되면 안 되는 것* 이 여기 있다 |

- **요건서만 승인하지 않는다.** 승인은 둘을 함께 보는 판단이며, 요건서만 승인받고 목업에서
  뒤집히면 **그 승인이 무효가 된다.**
- **목업에 요건서에 없는 것이 그려져 있으면 반려한다** — 그때는 요건서를 먼저 고쳐야 한다.
- **AI 가 모르는 것을 지어내지 않는다.** 요건서에 `[ASM-###]` 표시가 있으면 **그것이
  가정이다** — 승인 전에 확인한다.

### 승인할 때 볼 것

1. **컬럼과 조회조건이 실제 업무와 맞나** — 화면에 없는 것을 요구하고 있지 않은지.
2. **거부 테스트케이스가 있나** — *되면 안 되는 것* 이 없는 목업은 절반만 그려진 것이다.
3. **영향 테이블이 맞나** — 이 화면이 건드리는 테이블 목록. 예상 밖의 테이블이 있으면 묻는다.
4. **권한** — 누가 볼 수 있고 누가 실행할 수 있나.
5. **`[ASM-###]` 가 남아 있나** — 있으면 그것을 확정하고 넘어간다.

## 3. ④에서 AI 가 하는 일 — 무엇이 함께 만들어지나

| 것 | 왜 함께 |
|---|---|
| 백엔드·프론트 소스 | — |
| **생성 시드** (`generateSeeds`) | 메뉴·API·메시지 **키**. 선언을 고친 **그 자리에서** 돌려 **같은 커밋**에 담는다 |
| **손으로 쓰는 시드** | 메시지 **값** · 코드 · 권한 · 시퀀스 · 스키마 — `data/` 마지막 번호 + 1 로 **새 파일** |
| `manual.md` | **화면이 선 뒤에 만든다.** 화면이 서기 전의 사용법은 추측이다 |
| 테스트 | **TEST-FIRST** — TC 를 먼저 코드로 옮기고 실패를 확인한 뒤 구현한다 |

- **시드를 나중에 몰아서 만들지 않는다.** 시드 커밋이 뒤로 밀리면 그 사이 커밋에서
  `checkSeeds` 가 깨지고 **고친 사람이 아닌 사람의 PR 이 막힌다.**
- **이미 적재된 changeset 을 고치지 않는다** — 고치면 남의 로컬이 checksum 으로 죽는다.
- **셋 중 하나만 있는 상태로 커밋하지 않는다** — 요건서·목업·메뉴얼.

## 4. ⑤ 사용자 직접 테스트 — 왜 필요한가

**AI 의 게이트 초록이 이것을 갈음하지 않는다.** *돌아간 것* 과 *쓸 만한 것* 은 다르다.

AI 가 확인한 것 / 확인하지 못한 것을 가른다.

| AI 가 확인한다 | 사람만 확인할 수 있다 |
|---|---|
| 컴파일 · 타입 · 린트 | 이 화면이 **업무에 쓸 만한가** |
| 단위·통합 테스트 통과 | 조회 조건이 **실제로 쓰는 조건인가** |
| 아키텍처 규칙 · 시드 일치 | 버튼 위치·순서가 **손에 맞나** |
| 중복률 · 예외 삼킴 · 태깅 | 문구가 **업무 용어인가** |
| 규칙을 뒤집었을 때 테스트가 실패하는가 | 성능이 **체감상 견딜 만한가** |

> **테스트 통과만으로 완료를 선언하지 않는다.** AI 는 업무 규칙을 코드에서 뒤집었을 때
> 테스트가 실패하는지까지 확인한다(**규칙 뒤집기** —
> [`quality/redteam.md`](../aidd-guide/quality/redteam.md)). 그래도 위 오른쪽 칸은 남는다.

### 관리자 인증·배치 실제 PostgreSQL 시험

`sources/`에서 `./gradlew.bat :backend:bootstrap:systemAuthTest -PskipFrontend`를 별도로 실행한다.
일반 `check`의 단위·통합 시험에 더하여 실제 제품 bootstrap·HTTP·JPA·동시 트랜잭션을 검증하는 태스크다.
`SYSTEM_AUTH_JDBC`, `SYSTEM_AUTH_USER`, `SYSTEM_AUTH_PASSWORD`를 작업 전용 PostgreSQL에 설정해야 한다.
JDBC URL은 `jdbc:postgresql://127.0.0.1:<port>/auth520<고유이름>` 형식만 허용하며, 빈 DB에 제품 Liquibase를 적용한다.
기존 개발·운영 DB를 연결하지 않는다. DB 설정이 없거나 형식이 다르면 시험을 생략하지 않고 준비 오류로 실패한다.
시험은 HTTP 서버·클라이언트를 종료하며, DB 클러스터를 직접 기동했다면 실행자가 같은 전용 클러스터를 종료한다.

### 공통 JDBC/Redis v2 실제 시험

`sources/`에서 `./gradlew.bat :backend:api:authV2LiveTest -PskipFrontend`를 별도로 실행한다.
`AUTH_V2_TEST_JDBC=jdbc:postgresql://127.0.0.1:<port>/auth308<고유이름>`,
`AUTH_V2_TEST_USER`, `AUTH_V2_TEST_PASSWORD`, `AUTH_V2_REDIS_PORT`, `AUTH_V2_REDIS_PASSWORD`는 작업 전용 환경만 지정한다.
실제 Redis 중단/재기동 시험은 workspace 안의 `AUTH_V2_REDIS_SERVER` 실행 파일과
`AUTH_V2_REDIS_CONFIG` 설정 파일도 요구한다. 서비스/PATH 등록 없이 loopback 임의 포트에서 실행한다.
설정이 없거나 기존 DB 형식이면 준비 실패이며 skip하지 않는다. 실행자가 최초/재기동 Redis와 PG의 포트 종료까지 확인한다.
일반 `check`는 이 실환경 태스크를 대신하지 않는다. Windows에서 실행한 community redis-windows 빌드의
실제 프로토콜 시험은 Linux 운영 배포 수락과 구별한다.

SLICE-308의 공개 설치점은 `SessionV2Assembly.create`와 `verifyInstallation`이다.
후자는 자동 Spring Session/이전 필터 중복과 관측된 정확 handler/method/template을 함께 확인한다.
서블릿 fixture의 `verifyFilterInstallation`만으로 실제 MVC 등록 대조를 완료했다고 하지 않는다.
관리조회 준비는 정확 operation의 `Preflight`에서 입력을 읽고 `SessionInspectionPort.prepare`로 만들며,
같은 본문을 controller가 재독한 뒤 `Scope.inspectionFor`로 대상 일치를 재검사한다.
Redis 네트워크를 소유 guard 안에서 호출하지 않는다. 실제 system 설치/경로·화면 활성화는 SLICE-520에서 한다.

변이 시험은 `-PmutationTargets=... -PmutationTests=... -PmutationThreads=1`로 범위를 명시한다.
공유 Redis를 실제 중단하는 시험은 직렬 독립 실행하고, PIT에서도 함께 실행할 경우 직렬화한다.

## 5. ⑥ 개선 요청 — 어디로 돌아가나

| 요청의 성격 | 어디로 |
|---|---|
| 컬럼 추가 · 검증 변경 · 버튼 추가 · 권한 변경 | **①로** — 요건이 바뀌었다 |
| 문구 · 정렬 · 색 · 여백 | **④로** — 요건이 바뀌지 않았다 |
| G3 이후의 기능 범위 변경 | **CR 절차** — [01-project.md §7](01-project.md) |

**요청이 작아 보여도 ①을 거치는 것이 기본이다.** *버튼 하나* 가 검증·권한·영향 테이블을
건드리는 일이 흔하다. **소스만 고치고 문서를 두면 다음 세션이 낡은 요건서를 읽는다.**

## 6. 문서가 소스와 어긋날 때

**소스가 이긴다.** 문서는 구현을 따라온다 — 둘이 어긋나면 *문서가 낡은 것* 이 기본 가정이다.
단 **어긋남을 보고 없이 지나가지 않는다.**

AI 가 화면 소스를 고치면 `doc-drift` 훅이 그 화면의 문서 셋을 가리킨다 —
**차단이 아니라 경고**다. *문서를 고쳐야 하는가* 는 판단이고, **일치하면 고치지 않는다.**

## 7. 이미 만들어진 화면을 문서화할 때

순서가 거꾸로다 — 요건서가 **소스를 따라** 쓰인다. 이때 **역으로 작성했다는 사실을 문서
머리에 적는다.** 그러지 않으면 다음 사람이 그것을 *승인받은 요건* 으로 읽는다.

## 8. 지금 이 프로젝트의 화면 상태

- `documents/screen/` 에 있는 문서 셋은 **백오피스 템플릿의 화면들**이다 —
  사용자·역할·메뉴·권한·공통코드·메시지·로그·배치 등.
- **Agentic AI Hub 의 화면 43개(admin 32 · portal 11)는 문서 셋이 없다.**
  목록만 [`documents/spec/60-screens/_index.yaml`](../documents/spec/60-screens/_index.yaml) 에 있다.
- **작성 순서는 그 파일의 `doc_order` 가 정한다** — P1 메뉴가 먼저이고, 그중 **스키마
  카탈로그(SCR-003)와 업무 지식 관리(SCR-010~014)를 앞에 둔다.** DB 설계의 설정 테이블
  대부분이 거기서 나오고 agent 의 컨텍스트 구성이 그 화면들의 산출물을 입력으로 삼는다.

```bash
node .claude/skills/menu-doc/screens.mjs
```

어느 화면이 문서 셋을 갖췄는지 센다.

## 관련

- 화면 문서 셋의 형식 — [`documents/screen/README.md`](../documents/screen/README.md) ·
  [`aidd-guide/artifact/template/`](../aidd-guide/artifact/template/02-requirements.md))
- 화면 레시피 — [`aidd-guide/playbook/INDEX.md`](../aidd-guide/playbook/INDEX.md)
  (CRUD · 마스터디테일 · 마스터탭 · 트리 · 엑셀 · 모달 · changeset)
- 개발 표준 — [`aidd-guide/standard/INDEX.md`](../aidd-guide/standard/INDEX.md) ·
  Hub 전용 규약 [`hub-conventions.md`](../aidd-guide/standard/hub-conventions.md)
- 슬라이스 규약 — [`aidd-guide/process/07-slice.md`](../aidd-guide/process/07-slice.md)

# ASM-003 오케스트레이션 실환경 재현 — 2026-11-10

비규범 실행 근거다. 정본은 REQ-107/109·FEAT-210/211·FEAT-304, 현행 판정 기준은 [ADR-048 §3.1](../decisions/ADR-048-설계재현과-운영API-수락분리.md)과 [ADR-001 §8.1](../decisions/ADR-001-오케스트레이션-프레임워크.md)과 [ADR-003](../decisions/ADR-003-상태-원천-단일화.md)이다. 원본 과거 PoC를 발견한 것이 아니라 고정 버전으로 재구성했다. 기존 감사/이력/품질 목업은 사용하지 않았다.

## 판정과 한계

**ASM-003은 2026-11-10 승인된 새 설계 기준으로 확인됨으로 재판정했다.** PostgreSQL 자체 saver·별도 JVM 재개/교정·훅 순서·Hub 정책 소유 원칙을 재현했고, 아래 정상 종결 오표기를 수정한 재시험30단언이 통과했다. 중단 접수 ACK 및 CLI interrupted도 확인했지만 **중단된 호출의 최종 사용량은 제공되지 않았다**. CLI 접수/종결은 공급자 원격 종료/최종 과금의 증명이 아니다.

[ADR-048 A](../decisions/ADR-048-설계재현과-운영API-수락분리.md)는 사용자 승인으로 설계 재현과 운영 API 수락의 판정 범위를 분리했다. 아래 재판정은 그 승인 뒤 실행 근거를 다시 대조한 결과다. 운영 API 시험과 integration/system/agent G3 승인을 의미하지 않는다.

## 승인 후 새 기준 대조

| 항목 | 새 기준에 대응하는 실행 근거 | 판정 |
|---|---|---|
| 실제 PostgreSQL 자체 saver | 별도 JVM 재개·교정 현재 회차만 보존·과거 감사 유지·checkpoint/감사 실패 원자 롤백 단언 | 충족 |
| 실모델 스트림과 관측 보존 | 정상399청크·중단14중2수락, 모든 수신 관측 저장, completed/interrupted와 DB 상태·감사·최종checkpoint 직접 대조. 미제공량 UNKNOWN. CLI 이전누적/오류0을 최종량으로 선택하는 반례 검사 | 충족 |
| 고정 훅 순서 | BeforeCall→interrupt→WrapCall→본문→AfterCall 및 interrupt 시 Wrap/본문 제외 | 충족 |
| Hub 소유 통제 | quota/auth/security 거부 시본문0·8개 점유경합1성공·점유 우회 변이 탐지. 정상→취소 오표기 변이도 직접 상태 검사에서 탐지 | 충족 |

결과30단언과 두 변이의 실패 지점·종료된 전용 DB, 아래 재현 소스의 동일성을 다시 대조했다. 새 기준을 충족하는 기존 실행을 재판정한 것이며 승인 후 모델을 새로 호출했다고 주장하지 않는다.

## 정상 종결 오표기 보완과 재검증

독립 검토에서 이전 liveCall이 정상 호출에도 CANCEL_TERMINAL·CANCELLED_LOCAL·cancelled=true를 기록하는 **시험 코드 오류**를 발견했다. 이전23단언은 스트림/사용량 존재만 검사해 이 오류를 놓쳤다. 제품 소스 오류가 아니며, 이전 통과를 정상 DB 종결의 검증으로 인용하면 안 된다.

정상 호출은 COMPLETED·CALL_COMPLETED·cancelled=false, 중단 호출은 CANCELLED_LOCAL·CANCEL_TERMINAL·cancelled=true로 분리했다. CLI의 completed/interrupted 사건, DB 상태/취소 요청값, 감사 종류와 반대 종류0건, PostgreSQL에 저장한 최종 그래프 상태를 직접 검사했다. Usage는 관측 존재 여부를 보존하되 제공되지 않은 양을0으로 만들지 않는다. 재시험에서는 CLI hooks도 비활성화해 프로젝트 주의 문구가 합성 응답에 섞이는 경로를 닫았다.

| ADR-001 기준 | 입력과 기대 | 수정 후 실제값·판정 |
|---|---|---|
| 1. PostgreSQL 자체 saver | 중단 상태를 저장하고 다른 JVM에서 재개. 교정 후 현재 회차 checkpoint만 유지, 과거 감사 보존. 감사 실패는 checkpoint와 함께 rollback | 실제 PostgreSQL16에서 재통과. 프레임워크 가능성 실증이며 제품 상태/감사 전체 통합 시험은 아니다 |
| 2. 실모델 스트리밍/중단/계량 | 정상/중단 각 합성 숫자 생성. 청크 경계에서 Hub 반영을 멈추고 수신 관측·사용량·정상/중단 상태를 구별해 기록 | 정상399 delta 모두 수락, 양수 input/output·CLI completed·DB COMPLETED·CALL_COMPLETED·checkpoint cancelled=false. 중단14 delta 중2만 수락하고12개는 비공개 관측 보존. ACK/interrupted·DB CANCELLED_LOCAL·CANCEL_TERMINAL·checkpoint cancelled=true. 최종량 부재 UNKNOWN. **원 기준은 부분 실증, ADR-048 설계 기준은 충족** |
| 3. 고정 버전 훅 순서 | BeforeCall→interrupt→WrapCall→본문→AfterCall; interrupt이면 Wrap/본문/After0 | LangGraph4j1.8.25에서 순서 재확인. 중단 이력은 Wrap 밖에서 기록 |
| 4. Hub 통제 소유 | quota/auth/security 각각 거부 시본문0, stale fence 쓰기0,8개 동시점유 중1개 성공 | 모두 재통과. 정책/체크포인트/관측 DB는 시험 Hub 코드 소유. 실제 제품 권한/쿼터 구현 완료는 아니다 |

수신2번째 청크에서 DB 중단 요청을 확정하고 다음 경계부터 출력 반영을 차단했다. 뒤의12개는 전송/프로세스 큐를 포함하므로 원격 ACK 이후 새로 생성됐다고 단정하지 않는다. 중단 요청부터 로컬 drain 종료까지2,647ms는 공급자 처리 시간/종료 SLA가 아니다. 정상 경로의 중단 경과시간은 NA다.

## 실행 환경·범위

- JDK25.0.3+9, PostgreSQL16.14, LangGraph4j-core1.8.25, async-generator4.3.1, PostgreSQL JDBC42.7.11, SLF4J2.0.18.
- Codex0.165.0 app-server stdio, 요청 모델gpt-6.1-sol/effort low. 서버의 실제 모델 별칭 해석은 독립 검증하지 않았다. CLI가 기존 구독 인증을 사용하며 시험 코드는 인증 파일/키 내용을 읽거나 기록하지 않는다.
- 숫자1~200 생성의 정상/중단2호출. 업무 자료·저장소 본문은 전송하지 않았다. 도구/웹/앱/하위 에이전트/훅을 비활성화한다. 운영 API·Claude의 LangGraph 경로는 시험하지 않았다. 별도 Claude 계량 관측은 [두 CLI 검증](model-provider-usage-verification.md)에 있다.
- 임시 loopback PostgreSQL의 새 DB/역할만 사용한다. 기존 DB 서비스/데이터와 제품 sources는 수정하지 않았다. 시험 클러스터는 종료했다.
- probe_*와 임시 ai_agent_checkpoint는 실행 원리 검사용이다. 제품 Liquibase·ENT-258~266 구현이 아니며 JSONB/Java 직렬화를 제품 물리 설계로 승격하지 않는다.
- 제품 Boot/Spring 트랜잭션 조립·실제 FEAT-210/211 원장·대량 상태/인덱스·WAS 장애 복구·공급자 최종 계량·운영 API는 후속 소유 검증이다.

## 수정 후 실행 결과

| 단계 | 검증 | 통과 횟수 |
|---|---|---|
| phase1 | 훅 순서·interrupt 시 Wrap/본문 제외·별도 중단 감사·PG checkpoint | 4 |
| phase2 | 새 JVM 재개·현재 회차 교정/과거 감사·필수 감사 실패 원자 롤백·오래된 점유 차단·quota/auth/security 거부·8개 점유경합 | 9 |
| live_normal | 정상 스트림/usage·수신 관측 전량 저장·양수 token 필드·CLI completed·DB 상태·감사 종류·checkpoint 취소값·미제공량 비조작 | 8 |
| live | 청크 경계 중단·수신 관측 전량 저장·출력 반영2개 제한·interrupt ACK/interrupted·DB 상태·감사 종류·checkpoint 취소값·미제공량 비조작 | 9 |

**30단언 통과**이며 제품 TC 통과 수가 아니다. 실행별 고유 임시 폴더는 results.json의 run_dir로 확인한다. 같은 원리의 정상/중단 공통 단언도 각각 실행한 횟수로 센다.

규칙 반전도 두 종류 모두 탐지했다. `--mutation`은 오래된 점유값을 현재값으로 바꿔 쓰기를 허용하는 변이를 `AssertionError: stale_fence_write_denied`로 잡았다. 모델 요청은 없다. `--mutation-terminal`은 정상 실모델1호출의 종결을 CANCELLED_LOCAL·CANCEL_TERMINAL·cancelled=true로 오표기했고, 스트림/사용량 검사는 통과한 뒤 새 직접 검사 `AssertionError: hub_terminal_state_matches_call_mode`에서 실패했다. 따라서 이전 검사의 사각지점을 새 검사가 실제로 탐지한다. 정상 실행1회와 변이2회 모두 전용 PostgreSQL 종료를 확인했다. 변이 시험을30개 정상 단언에 더해 세지 않는다.

이전23단언의 관측(정상454청크, 중단40중2수락, 사용량 부재)과 점유 변이 탐지는 당시 기록이다. 정상 DB 종결 오표기를 놓쳤으므로 수정 후 결과로 현재 상태 보존 근거를 교체했다. 이전 Windows pg_ctl 파이프 상속 대기는 제어 출력을 파일로 받아 보완했으며 실패 시행을 PASS로 세지 않았다.

## 남은 검증

[Codex·Claude 추가 실증](model-provider-usage-verification.md)은 종결 후 대기/저장 조회·Codex 재개에서도 이전 계량 재표시와 오류0을 확인했다. 중단 최종량 귀속 완전성은 여전히 미확인이다. 이는 변경 전 ADR-001 §8.1(2)의 미충족 근거다. ADR-048 승인 후에는 운영 API 수락 항목으로 남기며 ASM 설계 확인과 구별한다.

ADR-048 A 승인 뒤 네 설계 항목을 위와 같이 대조해 ASM을 확인했다. 운영 공급자의 최종량·취소·재시도 지원은 ARCH-032의 별도 미실행 수락 시험으로 남긴다. 실제 원장/쿼터 원자 통합·전송 후 기록 장애 복구는 소유 G3 이후 제품 시험이다. 운영 API는 구독 확보 후 실행하며, 프레임워크 시험을 운영 수락으로 확대하지 않는다.

## 재현 방법

아래 세 소스를 파일명대로 같은 시험 폴더에 복원한다. 제품 소스 폴더에 넣지 않는다. Python3·JDK25·로컬 PostgreSQL16 실행 파일 경로를 실행기 상단에서 맞춘다. 기존 Gradle 캐시에 PostgreSQL42.7.11/SLF4J2.0.18이 있어야 한다. `lib/`에 아래 Maven Central 공식 JAR와 검증된 SHA-256을 준비하고 `python -X utf8 run_probe.py`, 이어 `python -X utf8 run_probe.py --mutation`과 `python -X utf8 run_probe.py --mutation-terminal`을 순서대로 실행한다. terminal 변이는 정상 실모델1호출을 추가하며 실행기/클래스 폴더를 공유하므로 동시 실행하지 않는다. 실모델은 기존 CLI 구독 로그인이 필요하며 합성 요청만 보낸다. 새 클러스터 자동 종료가 실패하면 출력의 전용 pgdata 경로만 확인해 종료한다.

| 파일 | SHA-256 |
|---|---|
| async-generator-4.3.1.jar | `59561296ca50b1717697f1182f70c787993c295bb31ec4c5a189769edc8cd70b` |
| langgraph4j-core-1.8.25-sources.jar | `d0203a94c669502453e384c126da20a793472ec1a6127cab69a4ae5da720a63a` |
| langgraph4j-core-1.8.25.jar | `78511850724629af49352abfc749c5afeebb5065616c6e58ee17ec60c3a7cf96` |
| postgresql-42.7.11.jar | `1981b31d3993c58702783c1cddf10a34e48c1f413d70ff1cb6def0a143484647` |
| slf4j-api-2.0.18.jar | `44508fd1576500688c790b190acdd16fec4f8c79a3e0b900afd70503cf055f55` |

공식 배포: [LangGraph4j1.8.25](https://repo.maven.apache.org/maven2/org/bsc/langgraph4j/langgraph4j-core/1.8.25/), [async-generator4.3.1](https://repo.maven.apache.org/maven2/org/bsc/async/async-generator/4.3.1/). 프로토콜은 설치 CLI의 `app-server generate-json-schema` 결과를 우선 대조했으며 [공식 app-server 문서](https://developers.openai.com/codex/app-server)를 함께 확인했다.

### RuntimeProbe.java

<!-- reproduce-file: RuntimeProbe.java -->
```java
// ASM-003 설계 재현용. 제품 구현이나 제품 테이블 DDL이 아니다.
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.*;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.checkpoint.*;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class RuntimeProbe {
 static String url=System.getenv("PROBE_JDBC"), user="probe", pass=System.getenv("PROBE_PASSWORD");
 static ThreadLocal<Connection> tx=new ThreadLocal<>();
 static List<String> order=new ArrayList<>(); static boolean inject=false;
 interface Work<T>{T run(Connection c)throws Exception;}
 static <T>T db(Work<T>w){
  if(tx.get()!=null)try{return w.run(tx.get());}catch(Exception e){throw new RuntimeException(e);}
  try(Connection c=DriverManager.getConnection(url,user,pass)){
   c.setAutoCommit(false);tx.set(c);try{T r=w.run(c);c.commit();return r;}
   catch(Exception e){c.rollback();throw new RuntimeException(e);}finally{tx.remove();}
  }catch(SQLException e){throw new RuntimeException(e);}
 }
 static int exec(Connection c,String sql,Object...args)throws Exception{
  try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p.executeUpdate();}
 }
 static long count(String sql,Object...a){return db(c->{try(var p=c.prepareStatement(sql)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){r.next();return r.getLong(1);}}});}
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);System.out.println("PASS "+name);}
 static void audit(Connection c,String t,int rev,String kind,String detail)throws Exception{exec(c,"insert into probe_audit(turn_id,rev,kind,detail) values(?,?,?,?)",t,rev,kind,detail);}
 static void setup(){db(c->{
  exec(c,"create table probe_turn(id text primary key,rev int not null default 0,fence bigint not null default 1,lease_until timestamptz not null default clock_timestamp()+interval '10 minutes',approved boolean not null default false,cancel boolean not null default false,quota boolean not null default true,auth boolean not null default true,secure boolean not null default true,status text not null default 'RUNNING')");
  exec(c,"create table ai_agent_checkpoint(id uuid primary key,turn_id text not null references probe_turn(id),rev int not null,fence bigint not null,node text not null,next_node text not null,state bytea not null,seq bigserial unique)");
  exec(c,"create table probe_audit(seq bigserial primary key,turn_id text not null,rev int not null,kind text not null,detail text not null)");
  exec(c,"create table probe_observation(turn_id text not null,observation_key int not null,payload jsonb not null,accepted boolean not null,primary key(turn_id,observation_key))");
  exec(c,"create table probe_usage(turn_id text not null,event_key text not null,quantity bigint,source text not null,primary key(turn_id,event_key))");
  return null;});}
 static void newTurn(String t){db(c->{exec(c,"insert into probe_turn(id) values(?)",t);return null;});}
 static byte[] encode(Map<String,Object>m)throws Exception{var b=new ByteArrayOutputStream();try(var o=new ObjectOutputStream(b)){o.writeObject(new HashMap<>(m));}return b.toByteArray();}
 @SuppressWarnings("unchecked") static Map<String,Object>decode(byte[]b)throws Exception{try(var i=new ObjectInputStream(new ByteArrayInputStream(b))){return(Map<String,Object>)i.readObject();}}
 static int rev(AgentState s){return ((Number)s.data().getOrDefault("rev",0)).intValue();}
 static class Saver implements BaseCheckpointSaver {
  public Collection<Checkpoint>list(RunnableConfig cfg){return db(c->{var out=new ArrayList<Checkpoint>();
   try(var p=c.prepareStatement("select * from ai_agent_checkpoint where turn_id=? order by seq desc")){p.setString(1,threadId(cfg));try(var r=p.executeQuery()){while(r.next())out.add(Checkpoint.builder().id(r.getString("id")).state(decode(r.getBytes("state"))).nodeId(r.getString("node")).nextNodeId(r.getString("next_node")).build());}}return out;});}
  public Optional<Checkpoint>get(RunnableConfig cfg){return list(cfg).stream().filter(cp->cfg.checkPointId().isEmpty()||cp.getId().equals(cfg.checkPointId().get())).findFirst();}
  public RunnableConfig put(RunnableConfig cfg,Checkpoint cp){return db(c->{String t=threadId(cfg);int revision=((Number)cp.getState().getOrDefault("rev",0)).intValue();long fence=((Number)cp.getState().getOrDefault("fence",1L)).longValue();
   int n=exec(c,"update probe_turn set status=status where id=? and rev=? and fence=? and lease_until>clock_timestamp()",t,revision,System.getenv("MUTATE_FENCE")==null?fence:count("select fence from probe_turn where id=?",t));if(n!=1)throw new IllegalStateException("점유 또는 회차 상실");
   exec(c,"insert into ai_agent_checkpoint(id,turn_id,rev,fence,node,next_node,state) values(?::uuid,?,?,?,?,?,?) on conflict(id) do update set state=excluded.state,next_node=excluded.next_node",cp.getId(),t,revision,fence,cp.getNodeId(),cp.getNextNodeId(),encode(cp.getState()));
   audit(c,t,revision,"CHECKPOINT",cp.getId());if(inject)throw new IllegalStateException("필수 감사 저장 실패 주입");
   return RunnableConfig.builder(cfg).checkPointId(cp.getId()).build();});}
  public Tag release(RunnableConfig c){return new Tag(threadId(c),list(c));}
 }
 static class Node implements AsyncNodeActionWithConfig<AgentState>,InterruptableAction<AgentState>{
  final boolean live;Node(boolean live){this.live=live;}
  public Optional<InterruptionMetadata<AgentState>>interrupt(String id,AgentState s,RunnableConfig c){order.add("INTERRUPT");boolean stop=Boolean.TRUE.equals(s.data().get("stop"));return stop?Optional.of(InterruptionMetadata.builder(id,s).build()):Optional.empty();}
  public CompletableFuture<Map<String,Object>>apply(AgentState s,RunnableConfig c){order.add("BODY");String t=c.threadId().orElseThrow();
   db(x->{audit(x,t,rev(s),"BODY","entered");return null;});
   if(live)try{return completedFuture(liveCall(t));}catch(Exception e){return CompletableFuture.failedFuture(e);}
   return completedFuture(Map.of("value",((Number)s.data().getOrDefault("value",0)).intValue()+1));
  }
 }
 static CompiledGraph<AgentState> graph(boolean live)throws Exception{
  var g=new StateGraph<AgentState>(AgentState::new);g.addNode("work",new Node(live));g.addEdge(StateGraph.START,"work");g.addEdge("work",StateGraph.END);
  g.addBeforeCallNodeHook((node,s,cfg)->{order.add("BEFORE");return completedFuture(db(c->{String t=cfg.threadId().orElseThrow();
   try(var p=c.prepareStatement("select * from probe_turn where id=? for update")){p.setString(1,t);try(var r=p.executeQuery()){r.next();if(!r.getBoolean("quota")||!r.getBoolean("auth")||!r.getBoolean("secure"))throw new IllegalStateException("Hub 정책 거부");if(r.getLong("fence")!=((Number)s.data().getOrDefault("fence",1L)).longValue())throw new IllegalStateException("점유 상실");return Map.<String,Object>of("stop",r.getBoolean("cancel")||!r.getBoolean("approved"));}}}));});
  g.addWrapCallNodeHook((node,s,c,a)->{order.add("WRAP");return a.apply(s,c);});
  g.addAfterCallNodeHook((node,s,c,r)->{order.add("AFTER");return completedFuture(r);});
  return g.compile(CompileConfig.builder().checkpointSaver(new Saver()).build());
 }
 static RunnableConfig cfg(String t){return RunnableConfig.builder().threadId(t).build();}
 static void invoke(String t,Map<String,Object>input,boolean live)throws Exception{order.clear();graph(live).invoke(input,cfg(t));
  if(order.contains("INTERRUPT")&&!order.contains("WRAP"))db(c->{audit(c,t,0,"INTERRUPTED","outside wrap");exec(c,"update probe_turn set status='WAITING' where id=?",t);return null;});}
 static Map<String,Object>liveCall(String t)throws Exception{
  Process p=new ProcessBuilder("python","-X","utf8",System.getenv("PROBE_STREAM_SCRIPT")).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  int seq=0,deltas=0,accepted=0;boolean usage=false,terminal=false,tool=false;long requestAt=0;boolean cancelTest=t.equals("live");
  try(var r=p.inputReader(StandardCharsets.UTF_8);var w=p.outputWriter(StandardCharsets.UTF_8)){
   String line;while((line=r.readLine())!=null){final String payload=line;boolean delta=line.contains("\"kind\": \"delta\"");boolean allow=count("select count(*) from probe_turn where id=? and cancel=false",t)==1;
    final int key=++seq;db(c->{exec(c,"insert into probe_observation values(?,?,?::jsonb,?)",t,key,payload,delta&&allow);return null;});
    if(delta){deltas++;if(allow)accepted++;if(cancelTest&&deltas==2){db(c->{exec(c,"update probe_turn set cancel=true,status='CANCEL_REQUESTED' where id=?",t);audit(c,t,0,"CANCEL_REQUEST","chunk=2");return null;});requestAt=System.nanoTime();w.write("CANCEL\n");w.flush();}}
    if(line.contains("\"kind\": \"usage\""))usage=true;
    if(line.contains("\"kind\": \"terminal\""))terminal=true;
    if(line.contains("unexpected_tool")||line.contains("bridge_error"))tool=true;
   }
  }finally{if(!p.waitFor(10,TimeUnit.SECONDS))p.destroyForcibly();}
  final boolean hasUsage=usage;
  final boolean storedCancel=cancelTest||System.getenv("MUTATE_TERMINAL")!=null;
  db(c->{exec(c,"insert into probe_usage values(?,?,?,?)",t,"terminal",null,hasUsage?"PROVIDER_OBSERVED":"UNKNOWN");audit(c,t,0,storedCancel?"CANCEL_TERMINAL":"CALL_COMPLETED",hasUsage?"usage observed":"usage unknown, not zero");exec(c,"update probe_turn set status=? where id=?",storedCancel?"CANCELLED_LOCAL":"COMPLETED",t);return null;});
  if(cancelTest)check(deltas>=2&&accepted==2&&terminal&&!tool,"real_stream_chunk_boundary_stop");
  else check(deltas>0&&accepted==deltas&&terminal&&usage&&!tool,"real_normal_stream_and_usage_in_graph_db");
  check(count("select count(*) from probe_observation where turn_id=?",t)==seq,"all_received_events_persisted");
  if(cancelTest){check(count("select count(*) from probe_observation where turn_id=? and payload->>'kind'='delta' and accepted",t)==2,"no_output_after_cancel_boundary");check(count("select count(*) from probe_observation where turn_id=? and payload->>'kind'='cancel_ack' and payload->>'accepted'='true'",t)==1,"cli_interrupt_acknowledged");check(count("select count(*) from probe_observation where turn_id=? and payload->>'kind'='terminal' and payload->>'status'='interrupted'",t)==1,"cli_terminal_interrupted");}
  else {check(count("select count(*) from probe_observation where turn_id=? and payload->>'kind'='usage' and (payload->'usage'->'last'->>'inputTokens')::bigint>0 and (payload->'usage'->'last'->>'outputTokens')::bigint>0",t)>0,"normal_provider_token_fields_preserved");check(count("select count(*) from probe_observation where turn_id=? and payload->>'kind'='terminal' and payload->>'status'='completed'",t)==1,"cli_terminal_completed");}
  System.out.println("OBSERVED deltas="+deltas+" accepted="+accepted+" usage_present="+usage+" elapsed_after_cancel_ms="+(requestAt==0?"NA":Long.toString((System.nanoTime()-requestAt)/1000000)));
  return Map.of("cancelled",storedCancel,"acceptedChunks",accepted);
 }
 static void phase1()throws Exception{
  setup();newTurn("normal");db(c->{exec(c,"update probe_turn set approved=true where id='normal'");return null;});invoke("normal",Map.of("rev",0,"fence",1L),false);
  check(order.equals(List.of("BEFORE","INTERRUPT","WRAP","BODY","AFTER")),"hook_order_version_1_8_25");
  newTurn("resume");invoke("resume",Map.of("rev",0,"fence",1L),false);check(order.equals(List.of("BEFORE","INTERRUPT")),"interrupt_skips_wrap_and_body");
  check(count("select count(*) from probe_audit where turn_id='resume' and kind='INTERRUPTED'")==1,"interrupt_audit_outside_wrap");
  newTurn("correct");invoke("correct",Map.of("rev",0,"fence",1L),false);
  check(count("select count(*) from ai_agent_checkpoint where turn_id='resume'")>0,"postgres_saver_before_process_exit");
 }
 static void phase2()throws Exception{
  db(c->{exec(c,"update probe_turn set approved=true where id='resume'");return null;});invoke("resume",null,false);check(count("select count(*) from probe_audit where turn_id='resume' and kind='BODY'")==1,"resume_new_jvm_from_postgres");
  var graph=graph(false);var old=new Saver().get(cfg("correct")).orElseThrow();
  db(c->{exec(c,"update probe_turn set rev=1,approved=true where id='correct'");graph.updateState(RunnableConfig.builder(cfg("correct")).checkPointId(old.getId()).build(),Map.of("rev",1,"value",0));exec(c,"delete from ai_agent_checkpoint where turn_id='correct' and rev<1");audit(c,"correct",1,"CORRECT","new revision");return null;});
  invoke("correct",null,false);check(count("select count(*) from ai_agent_checkpoint where turn_id='correct' and rev<>1")==0,"correction_keeps_current_checkpoints_only");check(count("select count(*) from probe_audit where turn_id='correct' and rev=0")>0,"correction_preserves_previous_audit");
  long before=count("select count(*) from ai_agent_checkpoint");long auditBefore=count("select count(*) from probe_audit");inject=true;boolean failed=false;
  try{new Saver().put(cfg("normal"),Checkpoint.builder().state(Map.of("rev",0,"fence",1L)).nodeId("work").nextNodeId(StateGraph.END).build());}catch(RuntimeException e){failed=true;}finally{inject=false;}
  check(failed&&count("select count(*) from ai_agent_checkpoint")==before&&count("select count(*) from probe_audit")==auditBefore,"checkpoint_audit_failure_atomic_rollback");
  db(c->{exec(c,"update probe_turn set fence=2 where id='normal'");return null;});failed=false;
  try{new Saver().put(cfg("normal"),Checkpoint.builder().state(Map.of("rev",0,"fence",1L)).nodeId("work").nextNodeId(StateGraph.END).build());}catch(RuntimeException e){failed=true;}
  check(failed&&count("select count(*) from ai_agent_checkpoint")==before,"stale_fence_write_denied");
  for(String policy:List.of("quota","auth","secure")){String t="deny_"+policy;newTurn(t);db(c->{exec(c,"update probe_turn set approved=true,"+policy+"=false where id=?",t);return null;});boolean denied=false;try{invoke(t,Map.of("rev",0,"fence",1L),false);}catch(Exception e){denied=true;}check(denied&&count("select count(*) from probe_audit where turn_id=? and kind='BODY'",t)==0,"hub_"+policy+"_denies_before_body");}
  newTurn("race");db(c->{exec(c,"update probe_turn set lease_until=clock_timestamp()-interval '1 second' where id='race'");return null;});
  try(var pool=Executors.newFixedThreadPool(8)){var start=new CountDownLatch(1);var jobs=new ArrayList<Future<Integer>>();for(int i=0;i<8;i++)jobs.add(pool.submit(()->{start.await();return db(c->exec(c,"update probe_turn set fence=fence+1,lease_until=clock_timestamp()+interval '5 minutes' where id='race' and lease_until<=clock_timestamp()"));}));start.countDown();int winners=0;for(var job:jobs)winners+=job.get();check(winners==1,"postgres_eight_competing_acquires_one_winner");}
 }
 public static void main(String[]args)throws Exception{
  switch(args[0]){case "phase1"->phase1();case "phase2"->phase2();case "live","live_normal"->{String t=args[0];boolean cancelled=t.equals("live");newTurn(t);db(c->{exec(c,"update probe_turn set approved=true where id=?",t);return null;});invoke(t,Map.of("rev",0,"fence",1L),true);
   check(count("select count(*) from probe_turn where id=? and status=? and cancel=?",t,cancelled?"CANCELLED_LOCAL":"COMPLETED",cancelled)==1,"hub_terminal_state_matches_call_mode");
   check(count("select count(*) from probe_audit where turn_id=? and kind=?",t,cancelled?"CANCEL_TERMINAL":"CALL_COMPLETED")==1&&count("select count(*) from probe_audit where turn_id=? and kind=?",t,cancelled?"CALL_COMPLETED":"CANCEL_TERMINAL")==0,"hub_terminal_audit_matches_call_mode");
   check(Boolean.valueOf(cancelled).equals(new Saver().get(cfg(t)).orElseThrow().getState().get("cancelled")),"graph_checkpoint_cancel_flag_matches_call_mode");
   boolean observed=count("select count(*) from probe_observation where turn_id=? and payload->>'kind'='usage'",t)>0;
   check(count("select count(*) from probe_usage where turn_id=? and quantity is null and source=?",t,observed?"PROVIDER_OBSERVED":"UNKNOWN")==1,"hub_usage_observation_preserves_unknown");
  }default->throw new IllegalArgumentException("미지원 모드");}
 }
}
```

### model_stream.py

<!-- reproduce-file: model_stream.py -->
```python
"""합성 텍스트 전용 CLI 스트림 브리지. 원 자격/토큰은 읽거나 기록하지 않는다."""
import json,subprocess,threading,queue,time,sys,os
from pathlib import Path
ROOT=Path(__file__).resolve().parent
WORK=ROOT/'model-work';WORK.mkdir(exist_ok=True)
CODEX=Path(os.environ['LOCALAPPDATA'])/'Programs/OpenAI/Codex/bin/codex.exe'
proc=subprocess.Popen([str(CODEX),'-c','features.hooks=false','-c','features.shell_tool=false','-c','features.apply_patch_freeform=false','-c','features.multi_agent=false','-c','features.apps=false','-c','web_search="disabled"','-c','project_doc_max_bytes=0','app-server','--stdio'],cwd=WORK,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,encoding='utf-8',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
q=queue.Queue(); lock=threading.Lock(); seq=0; thread_id=None;turn_id=None;cancel=False;cancel_id=None
def emit(kind,**fields): print(json.dumps({'kind':kind,**fields},ensure_ascii=False),flush=True)
def send(method,params,notification=False):
 global seq
 with lock:
  seq+=1; obj={'method':method,'params':params}
  if not notification:obj['id']=seq
  proc.stdin.write(json.dumps(obj)+'\n');proc.stdin.flush()
  return seq
def reader():
 for line in proc.stdout:
  try:q.put(json.loads(line))
  except ValueError:pass
 q.put({'eof':True})
threading.Thread(target=reader,daemon=True).start()
def control():
 global cancel,cancel_id
 for line in sys.stdin:
  if line.strip()=='CANCEL' and not cancel and thread_id and turn_id:
   cancel=True;cancel_id=send('turn/interrupt',{'threadId':thread_id,'turnId':turn_id});emit('cancel_sent')
threading.Thread(target=control,daemon=True).start()
def response(ident):
 end=time.monotonic()+75
 while time.monotonic()<end:
  obj=q.get(timeout=max(1,end-time.monotonic()))
  if obj.get('id')==ident:
   if 'error' in obj: raise RuntimeError('RPC 요청 실패: '+json.dumps(obj['error']))
   return obj['result']
  if obj.get('eof'):raise RuntimeError('CLI 조기 종료')
 raise RuntimeError('RPC 제한 시간 초과')
try:
 response(send('initialize',{'clientInfo':{'name':'aihub_asm003_probe','version':'1.0'},'capabilities':{'experimentalApi':True}}))
 send('initialized',{},True)
 r=response(send('thread/start',{'model':'gpt-6.1-sol','cwd':str(WORK),'sandbox':'read-only','approvalPolicy':'never','ephemeral':True,'baseInstructions':'You are a synthetic text generator. Do not use tools, files, network, or skills. Only produce the requested text.','developerInstructions':'This is a bounded streaming verification. No tools. No workspace content.','config':{'model_reasoning_effort':'low','features.shell_tool':False,'features.apps':False,'features.multi_agent':False}}))
 thread_id=r['thread']['id']
 r=response(send('turn/start',{'threadId':thread_id,'effort':'low','input':[{'type':'text','text':'Print the integers from 1 through 200, one number per line. Do not use tools. No explanation or headings.','text_elements':[]}]}))
 turn_id=r['turn']['id'];emit('started',model='gpt-6.1-sol')
 end=time.monotonic()+120;completed=False;deadline=None
 while time.monotonic()<end:
  try:o=q.get(timeout=2 if completed else 20)
  except queue.Empty:
   if completed:break
   continue
  if o.get('eof'):break
  m=o.get('method','');p=o.get('params',{})
  if cancel_id and o.get('id')==cancel_id:emit('cancel_ack',accepted='error' not in o)
  if 'id' in o and 'method' in o:
   proc.stdin.write(json.dumps({'id':o['id'],'error':{'code':-32601,'message':'시험 경로는 도구를 허용하지 않는다'}})+'\n');proc.stdin.flush();emit('unexpected_tool',method=m)
  if m=='item/agentMessage/delta':emit('delta',text=p['delta'])
  elif m=='thread/tokenUsage/updated':emit('usage',usage=p['tokenUsage'])
  elif m=='turn/completed':
   emit('terminal',status=p['turn']['status']);completed=True;deadline=time.monotonic()+3
  elif m=='item/started' and p.get('item',{}).get('type') not in ['userMessage','agentMessage','reasoning']:
   emit('unexpected_tool',item_type=p.get('item',{}).get('type'))
  elif m=='error':emit('provider_error',code=p.get('error',{}).get('codexErrorInfo','unknown'))
  if completed and deadline and time.monotonic()>deadline:break
 if not completed:emit('terminal',status='unknown')
except Exception as e:
 emit('bridge_error',error=type(e).__name__,detail=str(e)[:500]);sys.exit(1)
finally:
 proc.terminate()
 try:proc.wait(timeout=8)
 except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=3)
```

### run_probe.py

<!-- reproduce-file: run_probe.py -->
```python
from pathlib import Path
import subprocess,os,socket,secrets,uuid,json,shutil,hashlib,sys
ROOT=Path(__file__).resolve().parent
(ROOT/'classes').mkdir(exist_ok=True)
PG=Path('D:/postgreSQL/pgsql16/bin');JDK=Path('D:/java/jdk-25.0.3+9/bin')
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
for group,artifact,version in [('org.postgresql','postgresql','42.7.11'),('org.slf4j','slf4j-api','2.0.18')]:
 jar=next((CACHE/group/artifact/version).rglob('*.jar'));shutil.copyfile(jar,ROOT/'lib'/jar.name)
CP=str(ROOT/'classes')+';'+str(ROOT/'lib/*')
RUN=ROOT/('run-'+uuid.uuid4().hex[:8]);RUN.mkdir();DATA=RUN/'pgdata';PASS=secrets.token_urlsafe(30)
with socket.socket() as sock:sock.bind(('127.0.0.1',0));PORT=sock.getsockname()[1]
env=os.environ.copy();env.update(PROBE_JDBC=f'jdbc:postgresql://127.0.0.1:{PORT}/asm003',PROBE_PASSWORD=PASS,PROBE_STREAM_SCRIPT=str(ROOT/'model_stream.py'))
def call(args,stdin=None,timeout=150):
 if Path(args[0]).name=='pg_ctl.exe':
  log=RUN/'pg-control.log'
  with log.open('w',encoding='utf-8') as stream:r=subprocess.run([str(x) for x in args],input=stdin,stdout=stream,stderr=stream,text=True,encoding='utf-8',env=env,timeout=timeout,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  r.stdout='';r.stderr=log.read_text(encoding='utf-8',errors='replace');return r
 return subprocess.run([str(x) for x in args],input=stdin,text=True,encoding='utf-8',errors='replace',capture_output=True,env=env,timeout=timeout,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
def require(r,name):
 if r.returncode:raise RuntimeError(name+' '+(r.stdout+r.stderr).replace(PASS,'[REDACTED]')[-5000:])
def sql(text):
 r=call([PG/'psql.exe','-h','127.0.0.1','-p',str(PORT),'-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-X','-q'],text);require(r,'제어 SQL')
mutation='--mutation' in sys.argv
terminal_mutation='--mutation-terminal' in sys.argv
if mutation and terminal_mutation:raise ValueError('변이는 한 번에 하나만 실행한다')
if mutation:env['MUTATE_FENCE']='1'
if terminal_mutation:env['MUTATE_TERMINAL']='1'
started=False;result={'passes':[],'observations':[],'real_model':'not_run' if mutation else 'gpt-6.1-sol via Codex app-server 0.165.0','mutation':'fence' if mutation else 'terminal' if terminal_mutation else None,'run_dir':str(RUN)}
try:
 r=call([JDK/'javac.exe','-encoding','UTF-8','-cp',CP,'-d',ROOT/'classes',ROOT/'RuntimeProbe.java']);require(r,'컴파일')
 require(call([PG/'initdb.exe','-D',DATA,'-U','postgres','--auth=trust','--encoding=UTF8','--locale-provider=icu','--icu-locale=ko-KR']),'초기화')
 require(call([PG/'pg_ctl.exe','-D',DATA,'-l',RUN/'postgres.log','-o',f'-p {PORT} -h 127.0.0.1 -F','-w','start']),'시작');started=True
 sql(f"CREATE ROLE probe LOGIN PASSWORD '{PASS}';\nCREATE DATABASE asm003 OWNER probe ENCODING 'UTF8' LOCALE_PROVIDER icu ICU_LOCALE 'ko-KR' TEMPLATE template0;")
 hba=DATA/'pg_hba.conf';hba.write_text('host all probe 127.0.0.1/32 scram-sha-256\n'+hba.read_text(encoding='utf-8'),encoding='utf-8');require(call([PG/'pg_ctl.exe','-D',DATA,'reload']),'암호 인증 적용')
 for phase in (['phase1','phase2'] if mutation else ['phase1','live_normal'] if terminal_mutation else ['phase1','phase2','live_normal','live']):
  print('START',phase,flush=True)
  r=call([JDK/'java.exe','-cp',CP,'RuntimeProbe',phase],timeout=200)
  safe=(r.stdout+r.stderr).replace(PASS,'[REDACTED]');(RUN/(phase+'.log')).write_text(safe,encoding='utf-8')
  print(safe,flush=True)
  if mutation and phase=='phase2':
   if r.returncode and 'AssertionError: stale_fence_write_denied' in safe:result['mutation_killed']=True;print('PASS mutation_killed_stale_fence_bypass',flush=True);break
   raise RuntimeError('변이가 살아남았다')
  if terminal_mutation and phase=='live_normal':
   if r.returncode and 'AssertionError: hub_terminal_state_matches_call_mode' in safe:result['mutation_killed']=True;print('PASS mutation_killed_normal_as_cancelled',flush=True);break
   raise RuntimeError('정상 종결 오표기 변이가 살아남았다')
  require(r,phase)
  result['passes'] += [line[5:] for line in r.stdout.splitlines() if line.startswith('PASS ')]
  result['observations'] += [{'phase':phase,'summary':line} for line in r.stdout.splitlines() if line.startswith('OBSERVED ')]
 result['result']='PASS'
finally:
 if started or (DATA/'postmaster.pid').exists():
  r=call([PG/'pg_ctl.exe','-D',DATA,'-m','fast','-w','stop']);result['temporary_cluster_stopped']=r.returncode==0;print('temporary_cluster_stopped',r.returncode==0,flush=True)
 result['dependencies']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'lib').glob('*.jar')}
 (ROOT/('mutation-results.json' if mutation else 'terminal-mutation-results.json' if terminal_mutation else 'results.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
```


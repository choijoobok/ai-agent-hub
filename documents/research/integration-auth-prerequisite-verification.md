# 인증 선행 integration 기술 검증

| 항목 | 내용 |
|---|---|
| 조사 시점 | 2026-11-10 |
| 성격 | 기술 평가 및 격리 실측(PoC), 제품 구현 수락 시험과 구별 |
| 근거로 쓰인 결정 | ADR-022·ADR-028·ADR-046. 제품 정본은 spec, 게이트 상태는 project.state.yaml |
| 상위 | REQ-403·REQ-501·REQ-608·CON-033·CON-034·NFR-010·NFR-022 |

## 1. 범위와 전제

사용자는 개발 CLI 검증을 진행하고 운영 API 실증은 구독 확보 후 수행하는 A를 명시 선택했다. 기존 Codex 구독 로그인으로 합성 입력 두 건(정상·로컬 종료)을 실행했고, 인증 없는 loopback HTTP 서버로 CLI 재전송을 관측했다. Claude 로그인과 OpenAI/Anthropic API 키 환경 변수는 없었고 해당 경로를 호출하지 않았다. 인증 파일/키 내용·업무 자료는 읽거나 전송하지 않았다. 요청 모델은 gpt-6.1-sol, effort low이며 CLI JSONL만으로 서버의 실제 모델 식별을 별도 검증하지는 않았다.

암호 검증은 기존 제품 클래스/처리 리소스를 임시 진입점에서 구동했다. 기존 PostgreSQL 서비스와 분리한 임시 PostgreSQL16 클러스터/DB를 새로 만들고 합성 비밀번호의 SCRAM 인증을 사용했다. 제품 DB·배포·sources는 변경하지 않았다. 임시 클러스터는 시험 후 종료했다. 키/평문은 메모리와 자식 환경에서 전달하며 YAML에는 암호문만 기록했다. 이 임시 도구는 설치용 비밀 관리 도구가 아니다.

## 2. 부팅·설정·키 교체 실증

Windows/JDK25.0.3+9, 저장소 관리 Boot4.1.1·Crypto7.1.1·Framework7.0.9와 실제 bootstrap runtimeClasspath를 사용했다. `-Pdeployment=hub -Pprofile=local -PincludeSample=false -PskipFrontend`로 처리된 리소스가 제품 master changelog를 선택한다. 원본 src/main/resources를 앞에 덧붙이면 이 구성이 무효화되므로 사용하지 않았다.

이전 축소 구성의 14 assertion은 암호 라이브러리/초기 Bean 소비 근거였다. 아래는 그것을 대신해 제품 DB/Liquibase/JPA와 설정 소비 경로를 확인한 별도 실험이며 제품 TC 통과 수가 아니다.

| 사례 | 결과 | 관측 |
|---|---|---|
| 키 없음·오키·다른 설정 용도(3건) | 통과 | 안전한 암호 검증 오류, DB pool 시작 전 중단·테이블 변화0 |
| 제품 첫 부팅 | 통과 | Liquibase6 changeset, 실제 DB 접속·JPA, 합성 행 보존 |
| 프로파일이 오류 기본 파일보다 우선 | 통과 | 선택한 값만 복호화·외부 파일 Origin 보존 |
| 환경변수가 오류 외부 파일보다 우선 | 통과 | SPRING_DATASOURCE_PASSWORD의 바인딩/실제 DB 접속 |
| 선택 파일 오류·오류 프로파일/환경변수 아래 정상값(3건) | 통과 | 정상 하위 값으로 폴백 없이 암호 검증 실패·DB pool 미시작 |
| 새 암호문+이전 키의 부분 교체 | 통과 | 부팅 실패, 기존 행/changelog 유지 |
| 새 암호문+새 키 | 통과 | 재기동 성공·기존 행/changelog 유지 |
| 새 키 유실 | 통과 | 부팅 실패, 기존 행/changelog 유지 |
| 이전 키+이전 암호문 복구 | 통과 | 이전 짝으로 재기동 성공·기존 행/changelog 유지 |

총13건 모두 통과했다. 첫 부팅 전 테이블1개(시험 보존 행), 이후39개(제품36+Liquibase2+시험1)다. 적용 changeset6개와 소스 changelog파일9개는 다른 단위다. 정상/실패 stdout·stderr를 합성 평문/키/전체 암호문과 대조한 누출은0건이다. 미사용 잘못된 암호화 설정은 모든 부팅 사례에 포함했다. 환경변수/외부 파일/프로파일의 특정 DB 비밀키 실증을 임의 배열/중첩/별칭 원천 전체 지원으로 확대하지 않는다.

별도 형식 검사16개가 통과했다. 실제 Crypto의 왕복·난수성·환경 키 이름 연결·16KiB 경계, 버전/프로파일 변경·padding/과대 인코딩·빈/잘린/잔여 payload·음수/과대 필드 길이·비정규 UTF-8·다른 설정키·암호문 변조를 포함한다. 안전한 예외에는 비밀 원인 객체를 붙이지 않았다.

### 발견한 결함과 처리

1. Boot4.1.1의 `SpringConfigurationPropertySource`는 원천 조회의 Exception을 잡고 다음 후보로 넘어갈 수 있다. getProperty에서 곧바로 복호화하는 작은 wrapper만으로 실패 폐쇄를 주장할 수 없었다. 암호문은 안전한 toString을 가진 불투명 값으로 운반하고, 선택 완료 후 Environment/Binder의 소비 변환에서 검증하게 했다. 정상 하위 값이 있는 반례 두 건도 DB pool 시작 전 실패했다.
2. `SystemEnvironmentPropertySource`는 getProperty만이 아니라 원천 Map/entrySet 기반 캐시로 읽힌다. 양쪽에 불투명 값을 제공하고 mapper를 다시 붙여 환경변수 우회를 막았다. 원천 순서와 파일 Origin은 유지했다.
3. Windows pg_ctl의 자식 서버가 캡처 pipe를 상속하면 서버가 떠도 시험 러너가 대기했다. pg_ctl 출력을 파일로 받아 종료 판정을 바로잡았다. 이는 제품 결함이 아니라 시험 도구 문제다.

### 남는 제품 이행 범위

P3/P4에는 등록 비밀키의 정규 이름/별칭, 모든 지원 원천·설정 진단/Actuator, 실제 운영 키 입력·접근권한·암호문과 분리 보관·복구본 인계를 계약/제품 시험으로 확정한다. 이번 복구는 메모리에 보관한 합성 이전 키/암호문 짝의 복구이며 외부 키 저장소의 백업 매체 복원 시험은 아니다. 두 노드 배출·실제 전환·부하·고객 비밀 복구도 별도다.

## 3. 실제 모델·전송·재시도·취소·사용량

| 경로/조건 | 실측 결과 | 판정 범위 |
|---|---|---|
| Codex0.165.0 정상 구독 호출 | 6.35초, AIHUB_PROBE_OK, turn.completed, 입력11288/출력10/cached0 | 개발 응답/완료 usage 관측, 정산 근거 아님 |
| 구독 호출 turn.started 후0.4초 프로세스 종료 | 로컬 종료, 완료 사건/usage 없음 | 원격 접수/종료·최종 과금 미확인 |
| 합성 HTTP503, request_max_retries=0/2 | 실제 POST1/3회, turn.failed, usage 없음 | custom provider 설정의 HTTP 재전송 차이 |
| 합성 부분 SSE 후 단절, stream_max_retries=0/2 | 실제 POST1/3회, 재연결 사건 후 turn.failed, usage 없음 | custom provider의 스트림 재전송 차이 |
| Claude Code2.1.314 | 설치 도움말·공식 선언, 로그인 없음 | 실제 호출 미검증 |
| 운영 OpenAI/Anthropic API | 호출 안 함 | 사용자 A/CON-033·034에 따라 구독 확보 후 최우선 실증 |

정상 응답의 입력 토큰은 CLI 기본 프롬프트를 포함하며 업무 입력 길이와 같지 않다. 도구 실행 사건은0건이다. HTTP 대역에는 Authorization이 전달되지 않았다. 대역의 부분 스트림은 실제 CLI가 completion 없이 단절로 판정했으나, 실제 공급자가 보낸 부분응답/계량 시험은 아니다.

개발 CLI는 사용자의 모델 전송 허용 아래 합성 응답·형식·미지원 처리 시험에 사용한다. 내장 ChatGPT 경로의 모든 내부 요청/자동 재시도를 Hub의 권한·중단·예산·필수 이력 검사 사이에 두었다는 근거는 없다. 따라서 이 경로를 업무 실행·정확 쿼터·원격 취소 확정 시험에 활성화하지 않는다. custom provider 재시도 설정을 내장 구독 경로에도 적용됐다고 간주하지 않는다. API 기준 공통 계약은 축소하지 않는다.

취소/단절의 usage 부재는 UNKNOWN이다. 최종 사용량0, 원격 취소 성공 또는 비용0을 만들어 채우지 않는다. 운영 API 구독 후 선정 SDK/전송 버전으로 재시도 차단/사전 통제, 취소 경합, 지연 사용량 귀속, 스트리밍/동시성을 재검증한다. 공식 문서의 선언·설치 도움말·현재 실측을 별도 근거로 남긴다.

## 4. 배포와 공통 인터페이스 소유 검토

현재 소스의 graceful shutdown/executor 대기는 실제 배출 완료·작업 인계의 증거가 아니다. BatchScheduler의 ready 등록, BatchRunner/ProgressHelper의 기록 후 메모리 큐, NodeId의 재기동 재사용 가능성을 대조했다. ARCH-028의 기동 식별·배정/배출/검수/복구와 RUN-202가 목표이며 실제 두 노드 시험은 구현 뒤다.

integration의 admin-auth-boundary/v1 소유 검토에서 두 저장소의 명시 주입/인덱스, 정확 영역 분기·기존 전역 인가 차단, 생성/회전·CSRF 발급 타입, 기존 직접 세션 저장의 교체 지점, 공통 패키지·트랜잭션·기동 검증5건을 보완했다. 재검토의 CredentialAdmission 정의/시각 중복·비밀 출력 경계도 정리했다. 상세는 FEAT-301.admin_interface_v1과 TC-30116/30117이 정본이다. SYS_LO_LOGIN의 기존 독립 정책은 유지한다. 기술 소유 검토 완료와 사용자 G3 동결·구현 수락은 다르다.

## 5. 수용과 후속

| 관측 | 처리 |
|---|---|
| 제품 구성과 임시 DB에서 암호 초기 소비·교체/복구 가능 | ADR-022/028 근거와 ARCH-032 상세안에 반영 |
| 원천 예외 삼킴·환경 캐시 우회 | 불투명 값의 선택 후 변환, 정상 하위 값 반례로 검증 |
| CLI 응답/사용량 및 하위 재전송 | 개발 실측 범위를 기록, 내부 통제 불명 기능은 미지원 |
| 취소 뒤 원격 종료/최종 사용량 | 미확인 유지, 0이나 성공으로 대체 금지 |
| 운영 API 실증 | 사용자 A에 따라 구독 확보 직후로 유예, 운영 완료 아님 |
| 공통 v1 소유 검토 | 누락 타입/배선 경계 보완, 동결은 소유 게이트 승인 별도 |

현재 게이트·미결 상태는 project.state.yaml이 관리한다. 이 연구 문서는 재현 근거이며 새로운 제품 요구나 사용자 승인을 대신하지 않는다.

## 6. 출처

모두 2026-11-10 접근. 외부 페이지는 이후 바뀔 수 있다.

- Spring Security, [Crypto Module](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html): 인증된 GCM 암호화/난수 IV. 설치 JAR 7.1.1을 별도로 확인했다.
- Spring Boot, [Encrypting Properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.encrypting): Environment 확장과 설정 해석 책임.
- OpenAI, [Non-interactive mode](https://developers.openai.com/codex/noninteractive/): JSONL 사건·usage·스키마 출력. 단일 호출 제어/과금 보장의 문서로 읽지 않았다.
- Anthropic, [Run Claude Code programmatically](https://code.claude.com/docs/en/headless): agent loop·bare·stream-json·종료 신호의 구별.
- OpenAI, [Background mode](https://developers.openai.com/api/docs/guides/background): 해당 모드의 취소 요청/상태 조회.
- Anthropic, [Streaming messages](https://platform.claude.com/docs/en/build-with-claude/streaming): 메시지/usage 사건과 스트림 실패.
- 로컬 소스: `BatchScheduler.java`, `BatchRunner.java`, `BatchHistoryWriter.java`, `ProgressHelper.java`, `NodeId.java`, `application.yml`, `gradle/libs.versions.toml`. 현재 구현 사실이 외부 문서보다 우선한다.

- OpenAI, [Config reference](https://learn.chatgpt.com/docs/config-file/config-reference), [Advanced config](https://learn.chatgpt.com/docs/config-file/config-advanced): custom provider의 request_max_retries/stream_max_retries 선언과 내장 경로 구분. 실제0/2 설정은 별도 loopback 실측했다.

## 7. 재현 방법

아래 코드를 `build/tmp/admin-physical-design/`의 표시된 파일에 복원한다. 경로 상수는 설치된 JDK/PostgreSQL/Codex 경로에 맞춘다. 실제 운영 키/DB/업무 입력을 넣지 않는다. product-boot-probe는 자기 소유 임시 클러스터만 만들고 종료하며 기존 서비스에 연결하지 않는다.

1. Gradle init script를 저장하고 sources에서 아래 클래스패스 내보내기를 실행한다.
2. 검증 Java 두 파일을 product-classes로 컴파일하고 META-INF/spring.factories에 `org.springframework.boot.EnvironmentPostProcessor=com.aiagent.ProductCryptoProbe$DecryptEnvironment` 한 줄을 쓴다.
3. Python 제품 부팅 시험을 실행한다. 별도 형식 시험은 합성32바이트 키를 AIHUB_PROBE_MASTER_KEY 자식 환경으로만 전달해 `com.aiagent.EnvelopeBoundaryProbe`를 실행한다.
4. 모델 시험의 기본/--stream은 로컬 합성 서버다. --live는 승인된 개발 구독 로그인에서 외부 모델 두 건을 전송한다. 로그인 자격 자체를 출력하거나 복사하지 않는다.

```powershell
.\gradlew.bat -I ..\build\tmp\admin-physical-design\crypto-probe\export-runtime.gradle :backend:bootstrap:exportPocRuntimeClasspath -Pdeployment=hub -Pprofile=local -PincludeSample=false -PskipFrontend --offline --console=plain
```

```powershell
python -X utf8 build/tmp/admin-physical-design/crypto-probe/product-boot-probe.py
python -X utf8 build/tmp/admin-physical-design/model-runtime-probe.py
python -X utf8 build/tmp/admin-physical-design/model-runtime-probe.py --stream
python -X utf8 build/tmp/admin-physical-design/model-runtime-probe.py --live
```

암호문/키를 셸 인자나 일반 출력으로 넘기지 않는다. 로그를 공유할 때는 합성 비밀 검사를 통과한 결과 요약만 사용한다. `product-boot-results.json`은 부팅13사례, model-probe JSON들은6사례의 요약이며 삭제해도 아래 코드로 재현할 수 있다. 실행 시간과 토큰 수는 재실행 시 달라질 수 있다.

### crypto-probe/export-runtime.gradle

SHA-256: `d1fa78699370a28b8854c41ec1c771585c651b43919154ac60260def6328d186`

```groovy
gradle.projectsEvaluated {
    def bootstrap = gradle.rootProject.project(':backend:bootstrap')
    bootstrap.tasks.register('exportPocRuntimeClasspath') {
        dependsOn bootstrap.tasks.named('classes')
        dependsOn bootstrap.sourceSets.main.runtimeClasspath
        doLast {
            def target = new File(gradle.rootProject.rootDir, '../build/tmp/admin-physical-design/crypto-probe/product-classpath.txt')
            target.setText(bootstrap.sourceSets.main.runtimeClasspath.asPath, 'UTF-8')
            println '검증용 제품 runtimeClasspath 내보내기 완료'
        }
    }
}
```

### crypto-probe/ProductCryptoProbe.java

SHA-256: `afc83163fda8d09d718a360b5b331f8c7b6badf3d3235ed1cb1483a61b51a066`

```java
package com.aiagent;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.boot.*;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.origin.*;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** 제품 소스를 바꾸지 않고 실제 제품 클래스/처리 리소스로 실행하는 검증 전용 진입점. */
public class ProductCryptoProbe {
  static final String PREFIX="ENC(v1.A256GCM.";
  static IllegalStateException safe() { return new IllegalStateException("설정 비밀 검증 실패"); }
  static byte[] key() {
    try {
      String s=System.getenv("AIHUB_PROBE_MASTER_KEY");
      if(s==null || !s.matches("[A-Za-z0-9_-]{43}"))throw safe();
      byte[] b=Base64.getUrlDecoder().decode(s);
      if(b.length!=32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(b).equals(s))throw safe();
      return b;
    }catch(Exception e){throw safe();}
  }
  static String canonical(String name) {
    return name.indexOf('_')>=0 ? name.toLowerCase(Locale.ROOT).replace('_','.') : name;
  }
  static byte[] payload(String property,String secret) throws Exception {
    var bytes=new ByteArrayOutputStream();
    try(var out=new DataOutputStream(bytes)) {
      for(String s:List.of("v1","A256GCM","synthetic","probe",property,secret)) {
        byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>16384)throw safe();out.writeInt(b.length);out.write(b);
      }
    }
    return bytes.toByteArray();
  }
  static String encrypt(String property,String secret) throws Exception {
    byte[] bytes=AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(key(),"AES")).build().encrypt(payload(property,secret));
    return PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+")";
  }
  static String decrypt(String text,String property) {
    try {
      if(!text.startsWith(PREFIX)||!text.endsWith(")"))throw safe();
      String encoded=text.substring(PREFIX.length(),text.length()-1);
      if(encoded.length()>87382 || !encoded.matches("[A-Za-z0-9_-]+"))throw safe();
      byte[] cipher=Base64.getUrlDecoder().decode(encoded);
      if(cipher.length<32||cipher.length>65536||!Base64.getUrlEncoder().withoutPadding().encodeToString(cipher).equals(encoded))throw safe();
      byte[] plain=AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(key(),"AES")).build().decrypt(cipher);
      try(var in=new DataInputStream(new ByteArrayInputStream(plain))) {
        List<String> fields=new ArrayList<>();
        for(int i=0;i<6;i++) {
          int n=in.readInt();if(n<0||n>16384||n>in.available())throw safe();
          fields.add(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(n))).toString());
        }
        if(in.available()!=0||!fields.subList(0,5).equals(List.of("v1","A256GCM","synthetic","probe",canonical(property))))throw safe();
        return fields.get(5);
      }finally{Arrays.fill(plain,(byte)0);}
    }catch(Exception e){throw safe();}
  }
  record ProtectedValue(String ciphertext,String property) {
    @Override public String toString(){return "[보호된 설정]";}
  }
  static Object decode(Object value,String name) {
    // 원천 선택 단계에서 던진 Exception은 Boot mapper가 삼켜 낮은 값으로 넘어갈 수 있다.
    // 선택을 유지한 불투명 값으로 전달하고 실제 String 변환 단계에서 검증한다.
    return value instanceof String s && s.startsWith("ENC(") ? new ProtectedValue(s,name) : value;
  }
  static final class Wrapped extends EnumerablePropertySource<Object> implements OriginLookup<String> {
    final EnumerablePropertySource<?> original;
    Wrapped(EnumerablePropertySource<?> source){super(source.getName(),source);original=source;}
    public String[] getPropertyNames(){return original.getPropertyNames();}
    public Object getProperty(String name){return decode(original.getProperty(name),name);}
    public Origin getOrigin(String name){return OriginLookup.getOrigin(original,name);}
  }
  static final class WrappedEnvironment extends SystemEnvironmentPropertySource implements OriginLookup<String> {
    final SystemEnvironmentPropertySource original;
    WrappedEnvironment(SystemEnvironmentPropertySource source){super(source.getName(),lazyEnvironment(source.getSource()));original=source;}
    public Object getProperty(String name){return decode(original.getProperty(name),name);}
    public Origin getOrigin(String name){return OriginLookup.getOrigin(original,name);}
  }
  static Map<String,Object> lazyEnvironment(Map<String,Object> original) {
    // Boot의 환경변수 mapper는 getProperty 대신 getSource().get을 사용한다.
    return new AbstractMap<>() {
      public Object get(Object key){return key instanceof String name ? decode(original.get(name),name) : null;}
      public boolean containsKey(Object key){return original.containsKey(key);}
      public Set<String> keySet(){return original.keySet();}
      public Set<Entry<String,Object>> entrySet(){
        Set<Entry<String,Object>> entries=new LinkedHashSet<>();
        original.forEach((name,value)->entries.add(new SimpleImmutableEntry<>(name,decode(value,name))));
        return Collections.unmodifiableSet(entries);
      }
    };
  }
  public static final class DecryptEnvironment implements EnvironmentPostProcessor,Ordered {
    public int getOrder(){return ConfigDataEnvironmentPostProcessor.ORDER+1;}
    public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication app) {
      var conversion=new ApplicationConversionService();
      conversion.addConverter(ProtectedValue.class,String.class,value -> decrypt(value.ciphertext(),value.property()));
      env.setConversionService(conversion);
      app.addInitializers(context -> context.getBeanFactory().registerSingleton("conversionService",conversion));
      List<PropertySource<?>> sources=new ArrayList<>();env.getPropertySources().forEach(sources::add);
      for(PropertySource<?> source:sources) {
        if(source.getName().equals("configurationProperties"))continue;
        if(source instanceof SystemEnvironmentPropertySource system)env.getPropertySources().replace(source.getName(),new WrappedEnvironment(system));
        else if(source instanceof EnumerablePropertySource<?> enumerable)env.getPropertySources().replace(source.getName(),new Wrapped(enumerable));
      }
      // 같은 이름으로 원천을 교체한 뒤 기존 mapper 캐시를 승계하지 않는다.
      env.getPropertySources().remove("configurationProperties");
      ConfigurationPropertySources.attach(env);
    }
  }
  public static void main(String[] args)throws Exception {
    if(args.length>0&&args[0].equals("encode")) {System.out.print(encrypt(args[1],System.getenv("PROBE_INPUT_SECRET")));return;}
    var app=new SpringApplication(AiAgentApplication.class);
    app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> AiAgentApplication.requireActiveProfile(event.getEnvironment()));
    app.setRegisterShutdownHook(false);
    try(var context=app.run(args)) {
      var env=context.getEnvironment();
      String expected=System.getenv("PROBE_EXPECTED_PASSWORD");
      if(!Objects.equals(expected,env.getProperty("spring.datasource.password")))throw safe();
      context.getBean(jakarta.persistence.EntityManagerFactory.class);
      int changes;
      try(var connection=context.getBean(DataSource.class).getConnection();var statement=connection.createStatement()) {
        try(var rs=statement.executeQuery("SELECT count(*) FROM databasechangelog")){rs.next();changes=rs.getInt(1);}
        try(var rs=statement.executeQuery("SELECT marker FROM poc_sentinel WHERE id=1")){if(!rs.next()||!"preserved".equals(rs.getString(1)))throw safe();}
      }
      if(changes<1)throw safe();
      boolean origin=false;
      for(PropertySource<?> source:env.getPropertySources()) {
        if(source.getName().startsWith("Config resource")&&source.containsProperty("spring.datasource.password")) {
          origin=OriginLookup.getOrigin(source,"spring.datasource.password")!=null;break;
        }
      }
      System.out.println("POC_BOOT_OK changelog="+changes+" jpa=true db=true sentinel=true fileOrigin="+origin);
    }
  }
}
```

### crypto-probe/EnvelopeBoundaryProbe.java

SHA-256: `bd3afa3cb5d601ef69dd99430373e400e934e7cbd412c1f72ad9cb12e7b3c5b9`

```java
package com.aiagent;
import java.util.*;
import java.nio.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
public class EnvelopeBoundaryProbe {
  static int checks=0;
  static void check(boolean value){checks++;if(!value)throw new AssertionError("경계 검사 실패 "+checks);}
  static String pack(byte[] value){return ProductCryptoProbe.PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(ProductCryptoProbe.key(),"AES")).build().encrypt(value))+")";}
  static void reject(String value){boolean rejected=false;try{ProductCryptoProbe.decrypt(value,"spring.datasource.password");}catch(IllegalStateException e){rejected="설정 비밀 검증 실패".equals(e.getMessage())&&e.getCause()==null;}check(rejected);}
  public static void main(String[] args)throws Exception {
    String property="spring.datasource.password",secret="합성 값";
    String value=ProductCryptoProbe.encrypt(property,secret);
    check(ProductCryptoProbe.decrypt(value,property).equals(secret));
    check(!value.equals(ProductCryptoProbe.encrypt(property,secret)));
    check(ProductCryptoProbe.decrypt(value,"SPRING_DATASOURCE_PASSWORD").equals(secret));
    String limit="x".repeat(16384);check(ProductCryptoProbe.decrypt(ProductCryptoProbe.encrypt(property,limit),property).equals(limit));
    reject(value.replace("v1.","v2."));reject(value.replace("A256GCM.","OTHER."));
    reject(value.substring(0,value.length()-1)+"=)");reject("ENC(v1.A256GCM."+"A".repeat(87383)+")");
    reject(pack(new byte[0]));
    byte[] bytes=ProductCryptoProbe.payload(property,secret);
    reject(pack(Arrays.copyOf(bytes,bytes.length-1)));
    reject(pack(Arrays.copyOf(bytes,bytes.length+1)));
    byte[] negative=bytes.clone();ByteBuffer.wrap(negative).putInt(-1);reject(pack(negative));
    byte[] excessive=bytes.clone();ByteBuffer.wrap(excessive).putInt(16385);reject(pack(excessive));
    byte[] invalidUtf8=bytes.clone();invalidUtf8[4]=(byte)0xC0;invalidUtf8[5]=(byte)0xAF;reject(pack(invalidUtf8));
    reject(ProductCryptoProbe.encrypt("other.property",secret));
    byte[] corrupt=Base64.getUrlDecoder().decode(value.substring(ProductCryptoProbe.PREFIX.length(),value.length()-1));corrupt[corrupt.length-1]^=1;
    reject(ProductCryptoProbe.PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(corrupt)+")");
    System.out.println("PASS envelope-boundaries="+checks);
  }
}
```

### crypto-probe/product-boot-probe.py

SHA-256: `710465011bfdb442b5e9b80266ce78a49e2d608bea21f6f1ec5b7769041f05dd`

```python
from pathlib import Path
import subprocess,os,json,secrets,socket,uuid,time,re,base64

ROOT=Path(__file__).resolve().parent
PG=Path('D:/postgreSQL/pgsql16/bin')
JAVA='D:/java/jdk-25.0.3+9/bin/java.exe'
CP=str(ROOT/'product-classes')+';'+(ROOT/'product-classpath.txt').read_text(encoding='utf-8')
RUN=ROOT/('runtime-'+uuid.uuid4().hex[:10]);RUN.mkdir()
DATA=RUN/'pgdata';DB='aihub_crypto_probe';APP='probe_app'
PASSWORD=secrets.token_urlsafe(32)
KEY_A=base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=')
KEY_B=base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=')
with socket.socket() as s:s.bind(('127.0.0.1',0));PORT=s.getsockname()[1]
RESULTS=[];CIPHERS=[]
NO_WINDOW=getattr(subprocess,'CREATE_NO_WINDOW',0)

def call(args,stdin=None,env=None,timeout=90):
    if Path(args[0]).name=='pg_ctl.exe':
        # Windows 서버 자식이 pipe 핸들을 상속해 communicate가 기다리지 않도록 파일로 받는다.
        log=RUN/'pg-control.log'
        with log.open('w',encoding='utf-8') as stream:
            result=subprocess.run([str(x) for x in args],input=stdin,stdout=stream,stderr=stream,text=True,encoding='utf-8',env=env,timeout=timeout,creationflags=NO_WINDOW)
        result.stdout='';result.stderr=log.read_text(encoding='utf-8',errors='replace');return result
    return subprocess.run([str(x) for x in args],input=stdin,capture_output=True,text=True,encoding='utf-8',errors='replace',env=env,timeout=timeout,creationflags=NO_WINDOW)
def sql(text,db='postgres',as_app=False):
    env=os.environ.copy()
    if as_app:env['PGPASSWORD']=PASSWORD
    p=call([PG/'psql.exe','-X','-w','-h','127.0.0.1','-p',str(PORT),'-U',APP if as_app else 'postgres','-d',db,'-v','ON_ERROR_STOP=1','-At'],text,env)
    if p.returncode:raise RuntimeError('임시 DB SQL 실패: '+re.sub(re.escape(PASSWORD),'[REDACTED]',p.stderr)[-700:])
    return p.stdout.strip()
def encode(key,property='spring.datasource.password'):
    env=os.environ.copy();env.update(AIHUB_PROBE_MASTER_KEY=key,PROBE_INPUT_SECRET=PASSWORD)
    p=call([JAVA,'-cp',CP,'com.aiagent.ProductCryptoProbe','encode',property],env=env)
    if p.returncode or not p.stdout.startswith('ENC('):raise RuntimeError('암호문 생성 실패')
    CIPHERS.append(p.stdout);return p.stdout
def tables():return int(sql("SELECT count(*) FROM information_schema.tables WHERE table_schema='public';",DB,True))
def boot(name,cipher,key,expected_ok,env_cipher=None,base_cipher=None,extra_args=()):
    directory=RUN/name;directory.mkdir()
    # 모든 파일은 암호문뿐이며 키/평문 비밀은 자식 프로세스 환경으로만 전달한다.
    url=f'jdbc:postgresql://127.0.0.1:{PORT}/{DB}'
    config=f"spring:\n  datasource:\n    url: '{url}'\n    username: '{APP}'\n    password: '{base_cipher or cipher}'\nunused:\n  password: 'ENC(invalid-unused)'\n"
    (directory/'application.yml').write_text(config,encoding='utf-8')
    if base_cipher:
        (directory/'application-probe.yml').write_text("spring:\n  datasource:\n    password: '"+cipher+"'\n",encoding='utf-8')
    env=os.environ.copy();env.update(PROBE_EXPECTED_PASSWORD=PASSWORD)
    for k in ['AIHUB_PROBE_MASTER_KEY','SPRING_DATASOURCE_PASSWORD','DB_PASSWORD','SPRING_APPLICATION_JSON']:env.pop(k,None)
    if key is not None:env['AIHUB_PROBE_MASTER_KEY']=key
    if env_cipher is not None:env['SPRING_DATASOURCE_PASSWORD']=env_cipher
    before=tables();began=time.monotonic()
    args=[JAVA,'-cp',CP,'com.aiagent.ProductCryptoProbe','--spring.profiles.active=local,probe','--spring.config.additional-location='+directory.as_uri()+'/',
          '--spring.liquibase.contexts=product','--server.address=127.0.0.1','--server.port=0','--app.logging.sql.enabled=false',
          '--spring.main.banner-mode=off','--logging.level.root=INFO',*extra_args]
    p=call(args,env=env,timeout=150);out=p.stdout+p.stderr
    after=tables();ok=p.returncode==0 and 'POC_BOOT_OK' in out
    sensitive=[PASSWORD,KEY_A,KEY_B,*CIPHERS]
    leaked=any(s and s in out for s in sensitive)
    summary=re.search(r'POC_BOOT_OK[^\r\n]*',out)
    crypto_failure='설정 비밀 검증 실패' in out
    pool_started='HikariPool-1 - Starting' in out
    result=dict(case=name,passed=(ok==expected_ok and not leaked and (expected_ok or (before==after and crypto_failure and not pool_started))),boot_ok=ok,expected_ok=expected_ok,exit=p.returncode,
                seconds=round(time.monotonic()-began,2),public_tables_before=before,public_tables_after=after,secret_leak=leaked,
                crypto_failure=crypto_failure,pool_started=pool_started,
                evidence=summary.group(0) if summary else None)
    if not result['passed']:
        # 검사 실패 원인은 로컬 파일에 비밀을 마스킹한 뒤 보존한다.
        safe=out
        for item in sensitive:safe=safe.replace(item,'[REDACTED]')
        (directory/'failure-redacted.log').write_text(safe,encoding='utf-8')
        result['failure_log']=str(directory/'failure-redacted.log')
    RESULTS.append(result);print(json.dumps(result,ensure_ascii=False),flush=True)
    (ROOT/'product-boot-results.json').write_text(json.dumps(RESULTS,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if not result['passed']:raise RuntimeError('검증 실패 '+name)

started=False
try:
    p=call([PG/'initdb.exe','-D',DATA,'-U','postgres','--auth=trust','--encoding=UTF8','--locale-provider=icu','--icu-locale=ko-KR'])
    if p.returncode:raise RuntimeError('임시 클러스터 초기화 실패: '+p.stderr[-600:])
    p=call([PG/'pg_ctl.exe','-D',DATA,'-l',RUN/'postgres.log','-o',f'-p {PORT} -h 127.0.0.1 -F','-w','start'])
    if p.returncode:raise RuntimeError('임시 클러스터 시작 실패: '+p.stderr[-600:])
    started=True
    sql(f"CREATE ROLE {APP} LOGIN PASSWORD '{PASSWORD}';\nCREATE DATABASE {DB} OWNER {APP} ENCODING 'UTF8' LOCALE_PROVIDER icu ICU_LOCALE 'ko-KR' TEMPLATE template0;\n")
    sql('CREATE TABLE poc_sentinel(id integer primary key,marker varchar(20)); INSERT INTO poc_sentinel VALUES(1,\'preserved\');',DB,True)
    # 같은 임시 클러스터의 app 역할만 비밀번호 인증을 강제한다. postgres 제어는 loopback trust다.
    hba=DATA/'pg_hba.conf';hba.write_text(f'host all {APP} 127.0.0.1/32 scram-sha-256\n'+hba.read_text(encoding='utf-8'),encoding='utf-8')
    p=call([PG/'pg_ctl.exe','-D',DATA,'reload']);assert p.returncode==0
    a=encode(KEY_A);b=encode(KEY_B);wrong_scope=encode(KEY_A,'probe.other-secret')
    boot('missing-key-before-migration',a,None,False)
    boot('wrong-key-before-migration',a,KEY_B,False)
    boot('wrong-scope-before-migration',wrong_scope,KEY_A,False)
    boot('product-first-start',a,KEY_A,True)
    boot('profile-overrides-invalid-base',a,KEY_A,True,base_cipher='ENC(invalid-low-priority)')
    boot('environment-overrides-invalid-file','ENC(invalid-low-priority)',KEY_A,True,env_cipher=a)
    boot('selected-file-invalid', 'ENC(invalid-selected)',KEY_A,False)
    boot('invalid-profile-no-valid-base-fallback','ENC(invalid-selected)',KEY_A,False,base_cipher=a)
    boot('invalid-environment-no-valid-file-fallback',a,KEY_A,False,env_cipher='ENC(invalid-selected)')
    boot('partial-key-rotation',b,KEY_A,False)
    boot('key-rotation-complete',b,KEY_B,True)
    boot('key-loss-after-rotation',b,None,False)
    boot('restore-backup-pair',a,KEY_A,True)
    print(json.dumps(dict(result='PASS',cases=len(RESULTS),isolated_postgresql=True,product_sources_changed=False),ensure_ascii=False),flush=True)
finally:
    if started or (DATA/'postmaster.pid').exists():
        p=call([PG/'pg_ctl.exe','-D',DATA,'-m','fast','-w','stop'])
        print(json.dumps(dict(temporary_cluster_stopped=p.returncode==0),ensure_ascii=False),flush=True)
    (ROOT/'product-boot-results.json').write_text(json.dumps(RESULTS,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
```

### model-runtime-probe.py

SHA-256: `bb1a2619241b111dc028b6695add24dbade42d8b3aa4039ccec47a38a54ae812`

```python
from pathlib import Path
import subprocess, tempfile, threading, time, json, os, sys, re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT=Path(__file__).resolve().parent
CODEX=Path('C:/Users/최주복/AppData/Local/Programs/OpenAI/Codex/bin/codex.exe')
RESULTS=[]

def run_case(name, prompt, overrides=(), cancel_after=None, timeout=45):
    with tempfile.TemporaryDirectory(prefix='aihub-model-proof-',ignore_cleanup_errors=True) as scratch:
        args=[str(CODEX),'exec','--ignore-user-config','--strict-config','--skip-git-repo-check','--ephemeral','--sandbox','read-only','--json','--color','never','--model','gpt-6.1-sol',
              '-c','model_reasoning_effort="low"','-c','features.shell_tool=false','-c','agents.enabled=false','-c','web_search="disabled"']
        for val in overrides: args+=['-c',val]
        args += ['-']
        process=subprocess.Popen(args,cwd=scratch,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8',errors='replace')
        events=[]; err=[]; started=threading.Event()
        def read_out():
            for line in process.stdout:
                try:
                    e=json.loads(line); events.append(e)
                    if e.get('type')=='turn.started':started.set()
                except ValueError: pass
        def read_err():
            for line in process.stderr: err.append(line)
        threads=[threading.Thread(target=read_out,daemon=True),threading.Thread(target=read_err,daemon=True)]
        for t in threads:t.start()
        began=time.monotonic(); process.stdin.write(prompt+'\n');process.stdin.close()
        canceled=False; timed_out=False
        if cancel_after is not None:
            started.wait(15)
            if process.poll() is None:
                time.sleep(cancel_after)
                if process.poll() is None: process.terminate();canceled=True
        try:process.wait(timeout=max(1,timeout-(time.monotonic()-began)))
        except subprocess.TimeoutExpired:process.kill();process.wait();timed_out=True
        for t in threads:t.join(3)
        final=[e for e in events if e.get('type')=='turn.completed']
        messages=[e.get('item',{}).get('text','') for e in events if e.get('type')=='item.completed' and e.get('item',{}).get('type')=='agent_message']
        errtext=''.join(err)
        result=dict(case=name,exit=process.returncode,seconds=round(time.monotonic()-began,2),event_types=[e.get('type') for e in events],
          usage=final[-1].get('usage') if final else None,completed=bool(final),exact_probe_ok=messages==['AIHUB_PROBE_OK'],
          local_process_terminated=canceled,timeout=timed_out,tool_items=[e.get('item',{}).get('type') for e in events if e.get('item',{}).get('type') not in (None,'agent_message','reasoning')],
          diagnostics=dict(config_error='Error loading configuration' in errtext or 'unknown field' in errtext,network_error='error sending request' in errtext or 'failed to connect' in errtext,
            model_error=any('model' in str(e).lower() and ('not supported' in str(e).lower() or 'not found' in str(e).lower()) for e in events),event_errors=[e.get('message','')[:180] for e in events if e.get('type')=='error']))
        if not events:
            result['diagnostics']['startup_error']=re.sub(r'(sk-[A-Za-z0-9_-]+|Bearer\s+\S+)', '[REDACTED]', errtext)[-650:]
        RESULTS.append(result);print(json.dumps(result,ensure_ascii=False),flush=True)
        return result

if '--live' in sys.argv:
    first=run_case('codex-live-success','Reply with exactly AIHUB_PROBE_OK. Do not call any tool. This is a synthetic transport probe.')
    if first['completed']:
        run_case('codex-live-local-cancel','Write a 1500-word fictional explanation of sorting numbered blue blocks. Do not call tools. This is synthetic cancellation testing.',cancel_after=0.4,timeout=25)
elif '--stream' in sys.argv:
    for retries in [0,2]:
        observed=[]
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                self.rfile.read(int(self.headers.get('Content-Length','0')))
                observed.append(dict(path=self.path,authorization_present=bool(self.headers.get('Authorization'))))
                events=[
                  {'type':'response.created','response':{'id':'resp_probe','status':'in_progress','model':'gpt-6.1-sol','output':[]}},
                  {'type':'response.output_item.added','output_index':0,'item':{'id':'msg_probe','type':'message','role':'assistant','status':'in_progress','content':[]}},
                  {'type':'response.content_part.added','item_id':'msg_probe','output_index':0,'content_index':0,'part':{'type':'output_text','text':'','annotations':[]}},
                  {'type':'response.output_text.delta','item_id':'msg_probe','output_index':0,'content_index':0,'delta':'AIHUB_PARTIAL'}]
                self.send_response(200);self.send_header('Content-Type','text/event-stream');self.send_header('Connection','close');self.end_headers()
                for event in events:self.wfile.write(('event: '+event['type']+'\ndata: '+json.dumps(event)+'\n\n').encode());self.wfile.flush()
                self.close_connection=True
            def log_message(self,*args):pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        overrides=['model_provider="aihubeval"','model_providers.aihubeval.name="synthetic-local"',f'model_providers.aihubeval.base_url="http://127.0.0.1:{server.server_port}/v1"','model_providers.aihubeval.wire_api="responses"','model_providers.aihubeval.requires_openai_auth=false','model_providers.aihubeval.request_max_retries=0',f'model_providers.aihubeval.stream_max_retries={retries}']
        try:
            result=run_case('codex-partial-stream-retries-'+str(retries),'Reply with AIHUB_PROBE_OK. Do not use tools.',overrides,timeout=30)
            result['server_requests']=len(observed);result['partial_sent']=bool(observed);result['credential_forwarded']=any(x['authorization_present'] for x in observed)
            print(json.dumps(dict(observed=result['case'],requests=len(observed),credential_forwarded=result['credential_forwarded']),ensure_ascii=False),flush=True)
        finally:server.shutdown();server.server_close();thread.join(2)
else:
    # 실제 CLI의 HTTP 재전송을 통제 서버에서 관측한다. 실제 모델/인증 정보는 사용하지 않는다.
    for retries in [0,2]:
        observed=[]
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                size=int(self.headers.get('Content-Length','0'));self.rfile.read(size)
                observed.append(dict(path=self.path,authorization_present=bool(self.headers.get('Authorization'))))
                payload=b'{"error":{"message":"synthetic unavailable","type":"server_error","code":"probe_503"}}'
                self.send_response(503);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
            def log_message(self,*args): pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        overrides=['model_provider="aihubeval"','model_providers.aihubeval.name="synthetic-local"',f'model_providers.aihubeval.base_url="http://127.0.0.1:{server.server_port}/v1"','model_providers.aihubeval.wire_api="responses"','model_providers.aihubeval.requires_openai_auth=false',f'model_providers.aihubeval.request_max_retries={retries}','model_providers.aihubeval.stream_max_retries=0']
        try:
            result=run_case('codex-http503-retries-'+str(retries),'Reply with AIHUB_PROBE_OK. Do not use tools.',overrides,timeout=30)
            result['server_requests']=len(observed);result['paths']=list(dict.fromkeys(x['path'] for x in observed));result['credential_forwarded']=any(x['authorization_present'] for x in observed)
            print(json.dumps(dict(observed=result['case'],requests=len(observed),credential_forwarded=result['credential_forwarded']),ensure_ascii=False),flush=True)
        finally:server.shutdown();server.server_close();thread.join(2)
suffix='live' if '--live' in sys.argv else 'stream' if '--stream' in sys.argv else 'transport'
(ROOT/f'model-probe-{suffix}.json').write_text(json.dumps(RESULTS,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
```


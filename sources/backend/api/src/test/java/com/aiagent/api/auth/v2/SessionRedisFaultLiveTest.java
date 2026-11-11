// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.v2.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.*;

@Tag("authV2Live")
class SessionRedisFaultLiveTest {
    private static void command(OutputStream output,String...args)throws IOException{output.write(("*"+args.length+"\r\n").getBytes(StandardCharsets.UTF_8));for(String value:args){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);output.write(("$"+bytes.length+"\r\n").getBytes(StandardCharsets.UTF_8));output.write(bytes);output.write("\r\n".getBytes(StandardCharsets.UTF_8));}output.flush();}
    static void stopRedis()throws Exception{
        try(var socket=new Socket("127.0.0.1",Integer.parseInt(System.getenv("AUTH_V2_REDIS_PORT")))){socket.setSoTimeout(2000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));command(socket.getOutputStream(),"AUTH",System.getenv("AUTH_V2_REDIS_PASSWORD"));assertThat(reader.readLine()).isEqualTo("+OK");command(socket.getOutputStream(),"SHUTDOWN","NOSAVE");assertThat(reader.readLine()).isNull();}
    }
    static void restart(V2LiveFixture fixture)throws Exception{
        Path config=Path.of(System.getenv("AUTH_V2_REDIS_CONFIG")).toAbsolutePath().normalize(),server=Path.of(System.getenv("AUTH_V2_REDIS_SERVER")).toAbsolutePath().normalize();Path workspace=Path.of("../../..").toAbsolutePath().normalize();
        if(!config.startsWith(workspace.resolve("build/tmp/auth-v2-implementation"))||!server.startsWith(workspace.resolve("build/tmp/auth-v2-implementation")))throw new IllegalArgumentException("작업전용 Redis만 재시작한다");
        new ProcessBuilder(server.toString(),config.getFileName().toString()).directory(config.getParent().toFile()).redirectErrorStream(true).redirectOutput(config.getParent().resolve("redis-test-restart.log").toFile()).start();
        RuntimeException last=null;for(int attempt=0;attempt<30;attempt++){try{new RedisSessionStore(fixture.redis).verifyAvailable();return;}catch(RuntimeException failure){last=failure;Thread.sleep(100);}}throw new IllegalStateException("작업 Redis 재시작을 확인할 수 없다",last);
    }
    @Test @DisplayName("TC-30123/24 실제Redis중단/재기동에서암호DB확정유지·쿠키0·fallback0·새로그인")
    void realRedisOutageAfterPasswordCommit()throws Exception{
        try(var fixture=new V2LiveFixture(Provider.REDIS)){
            var runtime=fixture.runtime();var old=fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN));var change=fixture.scope(runtime,AuthDomain.ADMIN,Capability.PASSWORD_CHANGE,old.cookie());
            runtime.execute(change,()->{fixture.effect("password-committed");change.publish(fixture.producers.get(AuthDomain.ADMIN).rotation(change.exchange(),old.ref(),fixture.identity(AuthDomain.ADMIN,AuthLevel.AUTHENTICATED),fixture.ledger.now().plusSeconds(60)),new SessionRuntime.SlotPolicy(false,1,false));return null;});
            stopRedis();try{
                assertThatThrownBy(fixture::runtime).isInstanceOfAny(org.springframework.data.redis.RedisConnectionFailureException.class,org.springframework.dao.QueryTimeoutException.class);
                assertThatThrownBy(()->runtime.finish(change)).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(Outcome.REAUTH_REQUIRED));
                assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM AUTH307_CREDENTIAL",Integer.class)).isEqualTo(1);assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION",Integer.class)).isZero();assertThatThrownBy(()->change.emitCookies((d,c,t)->fail("장애쿠키"))).isInstanceOf(IllegalStateException.class);
            }finally{restart(fixture);}
            assertThatThrownBy(()->fixture.scope(runtime,AuthDomain.ADMIN,Capability.READ,old.cookie())).isInstanceOf(Failure.class);assertThat(fixture.login(runtime,AuthDomain.ADMIN,fixture.anonymous(runtime,AuthDomain.ADMIN)).cookie()).hasSize(43);
        }
    }
}

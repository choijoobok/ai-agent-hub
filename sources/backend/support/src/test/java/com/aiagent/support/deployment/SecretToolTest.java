// @feat FEAT-302
package com.aiagent.support.deployment;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class SecretToolTest {
    private final String key=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private final Map<String,String> env=Map.of(SecretEnvironment.MASTER,key,"NEW_TEST_KEY",Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1,2,3,4,5,6,7,8,9,0,1,2,3,4,5,6,7,8,9,0,1,2,3,4,5,6,7,8,9,0,1,2}));
    private record Result(int code,String out,String err){}
    private Result run(String command,String input,String... extra) {
        var args=new ArrayList<>(List.of(command,"--tenant","fixture","--environment","test","--property","spring.datasource.password"));args.addAll(List.of(extra));
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=SecretTool.run(args.toArray(String[]::new),new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8),env);
        return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    @Test @DisplayName("TC-3023 TC-3024 운영 도구는 암호문/검증 결과만 출력·부분 교체 거부")
    void safeCommands() {
        var encrypted=run("encrypt","synthetic-private","--secret-stdin");assertThat(encrypted.code).isZero();assertThat(encrypted.out).startsWith("ENC(v1.A256GCM.").doesNotContain(key).doesNotContain("synthetic-private");assertThat(encrypted.err).isEmpty();
        var verified=run("verify",encrypted.out);assertThat(verified).isEqualTo(new Result(0,"OK"+System.lineSeparator(),""));
        var fresh=run("reencrypt",encrypted.out,"--new-key-env","NEW_TEST_KEY");assertThat(fresh.code).isZero();assertThat(fresh.out).doesNotContain("synthetic-private");
        assertThat(run("verify",fresh.out).code).isEqualTo(2);assertThat(run("verify",fresh.out,"--key-env","NEW_TEST_KEY").code).isZero();
        var refused=run("encrypt","","--password","synthetic-private");assertThat(refused.code).isEqualTo(2);assertThat(refused.out).isEmpty();assertThat(refused.err).doesNotContain("synthetic-private").doesNotContain(key);
        var damaged=run("verify",encrypted.out.replace("v1.","v9."));assertThat(damaged.code).isEqualTo(2);assertThat(damaged.err).doesNotContain("ENC(");
    }
}

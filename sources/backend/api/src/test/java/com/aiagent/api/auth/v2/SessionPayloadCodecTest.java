// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.v2.SessionV2.*;
import java.io.*;
import org.junit.jupiter.api.*;

class SessionPayloadCodecTest {
    private byte[] corrupt(long seconds,int nanos)throws Exception{
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(2);out.writeUTF("ADMIN");out.writeLong(1);out.writeLong(1);out.writeUTF("00000000-0000-0000-0000-000000000001");out.writeLong(1);
            out.writeBoolean(true);out.writeUTF("owner");out.writeUTF("AUTHENTICATED");out.writeUTF("LOCAL");out.writeBoolean(false);out.writeLong(seconds);out.writeInt(nanos);
        }return bytes.toByteArray();
    }
    @Test @DisplayName("TC-30124 손상binary의범위밖epoch/nanos는401용INVALID_SESSION이다")
    void malformedInstantFailsClosed()throws Exception{
        for(var pair:new long[][]{{Long.MAX_VALUE,0},{Long.MAX_VALUE,Integer.MAX_VALUE},{Long.MIN_VALUE,Integer.MIN_VALUE}}){
            byte[] bytes=corrupt(pair[0],(int)pair[1]);
            assertThatThrownBy(()->SessionPayloadCodec.decode(bytes)).isInstanceOfSatisfying(Failure.class,failure->assertThat(failure.outcome()).isEqualTo(Outcome.INVALID_SESSION));
        }
    }
}

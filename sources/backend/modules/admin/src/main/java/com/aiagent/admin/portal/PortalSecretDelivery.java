// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.ManagedResult;
import java.util.function.Supplier;

/** 원 명령 원장 밖의 최초 HTTP 응답 한 번에만 전달한다. 종료/실패 시 반드시 지운다. */
public final class PortalSecretDelivery {
    public record Result(ManagedResult result,String password,String secretState) {
        @Override public String toString(){return "PortalCredentialResult[비밀 제외]";}
    }
    private static final class Slot {String password;}
    private final ThreadLocal<Slot> current=new ThreadLocal<>();
    public Result capture(Supplier<ManagedResult> operation) {
        if(current.get()!=null)throw new IllegalStateException("비밀번호 전달 문맥을 중첩할 수 없다");
        var slot=new Slot();current.set(slot);
        try {var result=operation.get();return new Result(result,slot.password,slot.password==null?"SECRET_NOT_REPLAYABLE":"ISSUED_ONCE");}
        finally {slot.password=null;current.remove();}
    }
    public void issue(String password){var slot=current.get();if(slot==null||slot.password!=null)throw new IllegalStateException("최초 응답 전달 문맥이 필요하다");slot.password=password;}
}

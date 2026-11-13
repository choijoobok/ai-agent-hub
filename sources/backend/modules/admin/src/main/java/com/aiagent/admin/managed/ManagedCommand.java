// @feat FEAT-040
package com.aiagent.admin.managed;

import java.util.*;

/** 관리값만 받는다. 주체·기능·서버 시각·이력 번호는 이 입력에 없다. */
public record ManagedCommand(String commandKey, String targetId, String expectedRevision,
                             String scope, Map<String,Object> values, String reason) {
    public ManagedCommand {
        requireKey(commandKey);
        if ((targetId==null)!=(expectedRevision==null)) throw ManagedErrorCode.VALIDATION.failure();
        if (targetId!=null) { positive(targetId); positive(expectedRevision); }
        if (scope==null || !scope.matches("[A-Za-z][A-Za-z0-9_-]{0,39}") || reason==null || reason.isBlank()
                || reason.codePointCount(0,reason.length())>2000 || values==null) throw ManagedErrorCode.VALIDATION.failure();
        values=ManagedDigest.freezeMap(values);
    }
    public static String requireKey(String value) {
        if (value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw ManagedErrorCode.VALIDATION.failure();
        return value;
    }
    public static long positive(String value) {
        if (value==null || !value.matches("[1-9][0-9]{0,18}")) throw ManagedErrorCode.VALIDATION.failure();
        try { return Long.parseLong(value); }
        catch (NumberFormatException failure) { throw ManagedErrorCode.VALIDATION.failure(); }
    }
}

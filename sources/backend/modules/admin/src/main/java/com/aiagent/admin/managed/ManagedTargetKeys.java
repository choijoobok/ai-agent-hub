// @feat FEAT-040 FEAT-010
package com.aiagent.admin.managed;

import java.util.*;

/** 기본은 BIGINT. 자연키 업무만 승인된 고정 코드 집합을 명시 설치한다. */
public final class ManagedTargetKeys {
    public static final ManagedTargetKeys DECIMAL=new ManagedTargetKeys(Set.of());
    private final Set<String> codes;
    private ManagedTargetKeys(Set<String> codes){this.codes=Set.copyOf(codes);}
    public static ManagedTargetKeys fixed(Set<String> codes) {
        if(codes==null||codes.isEmpty()||codes.stream().anyMatch(code->!isCode(code)))
            throw new IllegalArgumentException("고정 대상 코드 집합이 올바르지 않다");
        return new ManagedTargetKeys(codes);
    }
    private static boolean isCode(String value){return value!=null&&value.matches("[A-Z][A-Z0-9_]{0,29}");}
    public static String wire(String value) {
        if(!isCode(value))ManagedCommand.positive(value);
        return value;
    }
    public Object bind(String value) {
        if(codes.isEmpty())return ManagedCommand.positive(value);
        if(!codes.contains(value))throw ManagedErrorCode.VALIDATION.failure();
        return value;
    }
    boolean acceptsSqlType(String type) {
        return codes.isEmpty()?type.equals("int8"):Set.of("varchar","bpchar","text").contains(type);
    }
}

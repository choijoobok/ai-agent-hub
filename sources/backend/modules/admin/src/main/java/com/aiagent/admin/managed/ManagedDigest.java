// @feat FEAT-040
package com.aiagent.admin.managed;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class ManagedDigest {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private ManagedDigest() {}
    public static String of(String actor,String feature,String operation,ManagedCommand command) {
        var input=new TreeMap<String,Object>();
        input.put("version","managed/v1"); input.put("actor",actor); input.put("feature",feature);
        input.put("operation",operation); input.put("targetId",command.targetId()); input.put("expectedRevision",command.expectedRevision());
        input.put("scope",command.scope()); input.put("values",command.values()); input.put("reason",command.reason());
        return hash(JSON.writeValueAsString(input));
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256을 사용할 수 없다",failure); }
    }
    static Map<String,Object> freezeMap(Map<String,?> values) {
        @SuppressWarnings("unchecked") var result=(Map<String,Object>)freeze(values,0);
        return result;
    }
    private static Object freeze(Object value,int depth) {
        if (depth>32) throw ManagedErrorCode.VALIDATION.failure();
        if (value instanceof Map<?,?> map) {
            var result=new TreeMap<String,Object>();
            for(var entry:map.entrySet()) {
                if(!(entry.getKey() instanceof String key)) throw ManagedErrorCode.VALIDATION.failure();
                result.put(key,freeze(entry.getValue(),depth+1));
            }
            return Collections.unmodifiableMap(result);
        }
        if(value instanceof List<?> list) {
            if(list.size()>10_000) throw ManagedErrorCode.VALIDATION.failure();
            return Collections.unmodifiableList(list.stream().map(item->freeze(item,depth+1)).toList());
        }
        if(value==null || value instanceof String || value instanceof Boolean || value instanceof BigDecimal
                || value instanceof BigInteger || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return value;
        if(value instanceof Double d && Double.isFinite(d)) return BigDecimal.valueOf(d);
        if(value instanceof Float f && Float.isFinite(f)) return new BigDecimal(f.toString());
        throw ManagedErrorCode.VALIDATION.failure();
    }
}

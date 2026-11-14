// @feat FEAT-303
package com.aiagent.core.access;

import static com.aiagent.core.access.GroupAccess.*;
import java.math.*;
import java.util.*;
import java.util.function.Function;

/** 고객 합의 후 등록한 소스 구현만 사용한다. 제품 기본 마스킹 알고리즘을 추정하지 않는다. */
public final class MaskingRegistry {
    public record Rule(MaskRef ref,int rank,Set<ValueType> inputTypes,Function<Object,String> transform) {
        public Rule {Objects.requireNonNull(ref);if(rank<0)throw GroupAccess.invalid();inputTypes=Set.copyOf(inputTypes);if(inputTypes.isEmpty())throw GroupAccess.invalid();Objects.requireNonNull(transform);}
    }
    private final Map<MaskRef,Rule> rules;
    public MaskingRegistry(Collection<Rule> definitions) {
        var byRef=new HashMap<MaskRef,Rule>();var ranks=new HashSet<List<Object>>();
        for(var rule:definitions)if(byRef.putIfAbsent(rule.ref(),rule)!=null||!ranks.add(List.of(rule.ref().type(),rule.ref().version(),rule.rank())))throw GroupAccess.invalid();
        rules=Map.copyOf(byRef);
    }
    public Grant combine(Collection<Grant> grants,ValueType type) {
        Rule best=null;boolean raw=false;
        for(var grant:grants) {
            if(grant.mode()==Mode.RAW)raw=true;
            if(grant.mode()!=Mode.MASKED)continue;
            var rule=rules.get(grant.mask());
            if(rule==null||!rule.inputTypes().contains(type))return null;
            if(best!=null&&(!best.ref().type().equals(rule.ref().type())||!best.ref().version().equals(rule.ref().version())))return null;
            if(best==null||rule.rank()<best.rank())best=rule;
        }
        return raw?Grant.raw():best==null?Grant.denied():Grant.masked(best.ref());
    }
    String apply(MaskRef ref,ValueType type,Object value) {
        var rule=rules.get(ref);if(rule==null||!rule.inputTypes().contains(type)||!matches(type,value))throw GroupAccess.invalid();
        // NULL/빈 값 처리도 고객 구현의 계약이다. 실패/NULL 결과는 원값 대체 없이 닫는다.
        return Objects.requireNonNull(rule.transform().apply(value));
    }
    static boolean matches(ValueType type,Object value) {
        if(value==null)return true;
        return switch(type) {
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case NUMBER -> value instanceof BigDecimal||value instanceof BigInteger||value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long||value instanceof Double d&&Double.isFinite(d)||value instanceof Float f&&Float.isFinite(f);
        };
    }
}

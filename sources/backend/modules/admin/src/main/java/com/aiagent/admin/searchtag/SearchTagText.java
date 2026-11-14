// @feat FEAT-024
package com.aiagent.admin.searchtag;

import com.aiagent.admin.managed.ManagedErrorCode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** REQ-607.text_normalization v1. 원문 저장값을 바꾸지 않는 검색/중복 판정 함수. */
public final class SearchTagText {
    private SearchTagText() {}

    public static String normalize(String original) {
        if(original==null||original.codePointCount(0,original.length())>200)
            throw ManagedErrorCode.VALIDATION.failure();
        String compatible=Normalizer.normalize(original,Normalizer.Form.NFKC);
        int start=0,end=compatible.length();
        while(start<end&&space(compatible.codePointAt(start)))start+=Character.charCount(compatible.codePointAt(start));
        while(end>start&&space(compatible.codePointBefore(end)))end-=Character.charCount(compatible.codePointBefore(end));
        var result=new StringBuilder();
        compatible.substring(start,end).codePoints().forEach(c->result.appendCodePoint(c>='A'&&c<='Z'?c+('a'-'A'):c));
        String normalized=result.toString();
        if(normalized.isEmpty()||normalized.codePointCount(0,normalized.length())>400)
            throw ManagedErrorCode.VALIDATION.failure();
        return normalized;
    }

    private static boolean space(int codePoint) {
        return Character.isSpaceChar(codePoint)||(codePoint>=0x09&&codePoint<=0x0d)||codePoint==0x85;
    }

    /** 같은 부모에서만 유일하다. 다른 태그와의 동의어 일치는 복수 검색 결과로 허용한다. */
    public static List<String> synonyms(String representative,List<String> originals) {
        if(originals==null)throw ManagedErrorCode.VALIDATION.failure();
        var seen=new HashSet<String>();seen.add(normalize(representative));
        var normalized=new ArrayList<String>();
        for(String original:originals) {
            String text=normalize(original);
            if(!seen.add(text))throw ManagedErrorCode.VALIDATION.failure();
            normalized.add(text);
        }
        return List.copyOf(normalized);
    }
}

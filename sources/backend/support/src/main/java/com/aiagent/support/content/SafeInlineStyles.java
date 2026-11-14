// @feat FEAT-010
package com.aiagent.support.content;
import java.util.*;
final class SafeInlineStyles {
    private SafeInlineStyles(){}
    record Cleaned(String css,boolean removed) {}
    static Cleaned clean(String raw) {
        boolean removed=false;
        var kept=new LinkedHashMap<String,String>();
        for(String part:raw.split(";")) {
            if(part.isBlank())continue;
            int colon=part.indexOf(':');if(colon<1){removed=true;continue;}
            String key=part.substring(0,colon).strip().toLowerCase(Locale.ROOT),value=part.substring(colon+1).strip().toLowerCase(Locale.ROOT);
            boolean safe=switch(key) {
                case "color","background-color" -> value.matches("#[0-9a-f]{3}([0-9a-f]|[0-9a-f]{3}|[0-9a-f]{5})?")||Set.of("black","white","gray","grey","red","green","blue","yellow","orange","purple","teal","navy","maroon","lime","silver","aqua","fuchsia","olive","transparent","currentcolor").contains(value);
                case "font-size" -> value.matches("(?:[89]|[1-3][0-9]|4[0-8])px");
                case "font-weight" -> value.matches("normal|bold|[1-9]00");
                case "font-style" -> Set.of("normal","italic","oblique").contains(value);
                case "text-align" -> Set.of("left","right","center","justify","start","end").contains(value);
                case "text-decoration" -> Set.of("none","underline","overline","line-through").contains(value);
                case "white-space" -> Set.of("normal","pre","pre-wrap","pre-line","break-spaces","nowrap").contains(value);
                default -> false;
            };
            if(safe)kept.put(key,value);else removed=true;
        }
        return new Cleaned(String.join(";",kept.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList()),removed);
    }
}

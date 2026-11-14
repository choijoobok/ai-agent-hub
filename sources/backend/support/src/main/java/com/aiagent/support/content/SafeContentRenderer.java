// @feat FEAT-010
package com.aiagent.support.content;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.safety.*;

/** 원문 저장과 분리한 표시 정책. 네트워크를 사용하지 않으며 소비 모듈도 이 결과를 사용한다. */
public final class SafeContentRenderer {
    public static final String POLICY_VERSION="chat-content/1";
    public record Rendered(String policyVersion,String html,boolean removed,boolean hasContent) {}
    private final Parser markdown;
    private final HtmlRenderer html;
    private final Cleaner cleaner;
    public SafeContentRenderer() {
        var extensions=List.of(TablesExtension.builder().maxCells(10_000).build());
        markdown=Parser.builder().extensions(extensions).build();
        html=HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build();
        cleaner=new Cleaner(new Safelist().addTags("p","div","span","h1","h2","h3","h4","h5","h6","ul","ol","li","strong","b","em","i","s","del","u","blockquote","pre","code","br","hr","table","thead","tbody","tfoot","tr","th","td","a")
            .addAttributes(":all","style").addAttributes("a","href","title").addProtocols("a","href","http","https")
            .addEnforcedAttribute("a","rel","noopener noreferrer").addEnforcedAttribute("a","target","_blank"));
    }
    public Rendered render(String format,String body) {
        if(format==null||!Set.of("MARKDOWN","HTML","TEXT").contains(format)||body==null
                ||body.codePointCount(0,body.length())>200_000||body.getBytes(StandardCharsets.UTF_8).length>800_000
                ||body.codePoints().anyMatch(c->c==0||c>=0xD800&&c<=0xDFFF||Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))
            throw new IllegalArgumentException("지원하지 않는 형식 또는 본문 크기/문자다");
        if(format.equals("TEXT")) {
            var pre=new Element("pre").appendChild(new TextNode(body));
            return new Rendered(POLICY_VERSION,pre.outerHtml(),false,visible(body));
        }
        String rendered=format.equals("MARKDOWN")?html.render(markdown.parse(body)):body;
        var dirty=Jsoup.parseBodyFragment(rendered);dirty.outputSettings().prettyPrint(false);
        if(dirty.getElementsByTag("td").size()+dirty.getElementsByTag("th").size()>10_000)throw new IllegalArgumentException("표의 셀 수 상한을 초과했다");
        boolean removed=!cleaner.isValid(dirty);
        var clean=cleaner.clean(dirty);clean.outputSettings().prettyPrint(false);
        for(var element:clean.getAllElements()) {
            if(element.hasAttr("style")) {
                var style=SafeInlineStyles.clean(element.attr("style"));
                removed|=style.removed();
                if(style.css().isEmpty())element.removeAttr("style");else element.attr("style",style.css());
            }
        }
        String safe=clean.body().html();
        if(safe.getBytes(StandardCharsets.UTF_8).length>2_000_000)throw new IllegalArgumentException("표시 결과 상한을 초과했다");
        return new Rendered(POLICY_VERSION,safe,removed,visible(clean.body().text()));
    }
    private static boolean visible(String text){return text.codePoints().anyMatch(c->!Character.isWhitespace(c)&&!Character.isSpaceChar(c)&&Character.getType(c)!=Character.FORMAT&&!Character.isISOControl(c));}
}

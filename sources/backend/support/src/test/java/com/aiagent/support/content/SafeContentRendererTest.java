// @feat FEAT-010
package com.aiagent.support.content;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SafeContentRendererTest {
    private final SafeContentRenderer renderer=new SafeContentRenderer();
    @Test void markdownSupportsTableAndEscapesRawHtml() {
        var result=renderer.render("MARKDOWN","# 제목\n\n| 이름 | 값 |\n| --- | --- |\n| 예 | **강조** |\n\n<script>alert(1)</script>\n\n[안전](https://example.com) ![외부](https://example.com/a.png)");
        assertThat(result.html()).contains("<h1>","<table>","<strong>","&lt;script&gt;","https://example.com").doesNotContain("<script>","<img");
        assertThat(result.hasContent()).isTrue();assertThat(result.removed()).isTrue();
    }
    @Test void htmlRemovesActiveContentAndUnsafeCssButPreservesRawSupportedFormatting() {
        var result=renderer.render("HTML","<p onclick='x()' style='color:#123456;font-size:48px;position:fixed;background-image:url(https://bad)'>내용 <a href='javascript:alert(1)'>링크</a><img src='https://bad'></p><iframe src='https://bad'></iframe><script>bad()</script><style>body{display:none}</style>");
        assertThat(result.html()).contains("color:#123456","font-size:48px","내용").doesNotContain("onclick","javascript:","https://bad","position","<iframe","<script","<style","<img");
        assertThat(result.removed()).isTrue();
        assertThat(renderer.render("HTML","<p style='color: blue; FONT-SIZE: 12px;'>정상</p>").removed()).isFalse();
    }
    @Test void textIsLiteralAndEmptyRenderedMarkupIsNotUsable() {
        assertThat(renderer.render("TEXT","<b>원문</b>\n**글**").html()).contains("&lt;b&gt;","**글**","\n");
        assertThat(renderer.render("HTML","<script>alert(1)</script><img src='https://bad'><br>&nbsp;&#8203;").hasContent()).isFalse();
        assertThat(renderer.render("TEXT"," \n\t").hasContent()).isFalse();
    }
    @Test void formatsSizeAndControlCharactersAreStrict() {
        assertThatThrownBy(()->renderer.render("OTHER","본문"));
        assertThatThrownBy(()->renderer.render("TEXT","x".repeat(200001)));
        assertThatThrownBy(()->renderer.render("TEXT","본문\u0000"));
        assertThat(renderer.render("HTML","<span style='font-size:49px;color:expression(x);white-space:pre-wrap'>내용</span>").html()).contains("white-space:pre-wrap").doesNotContain("49px","expression");
    }
}

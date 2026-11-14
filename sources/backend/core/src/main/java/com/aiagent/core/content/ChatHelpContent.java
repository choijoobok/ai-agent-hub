// @feat FEAT-010
package com.aiagent.core.content;
/** 실제 소비자는 자신의 현재 권한/활성·보존 종료 검증을 등록해야 한다. 미등록 소비자는 닫힌다. */
public interface ChatHelpContent {
    record Use(String module,String kind,String key,String purpose){}
    record Document(String code,String name,String format,String body,String revision,String historyNo,String policyVersion,String html,boolean removed){}
    Document current(Use use,String code,String language);
    Document exact(Use use,String code,String revision,String policyVersion,String language);
}

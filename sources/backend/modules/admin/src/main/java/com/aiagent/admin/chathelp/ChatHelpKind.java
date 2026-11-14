// @feat FEAT-010
package com.aiagent.admin.chathelp;
import com.aiagent.admin.managed.*;
import java.util.*;
public enum ChatHelpKind {
    USAGE_HELP("usage"),CHAT_START("start"),INPUT_NOTICE("notice");
    public static final ManagedTargetKeys KEYS=ManagedTargetKeys.fixed(Set.of("USAGE_HELP","CHAT_START","INPUT_NOTICE"));
    private final String suffix;
    ChatHelpKind(String suffix){this.suffix=suffix;}
    public String nameKey(){return "chat_help."+suffix;}
    public String positionKey(){return nameKey()+"_position";}
    public String timingKey(){return nameKey()+"_timing";}
    public static ChatHelpKind require(String code){KEYS.bind(code);return valueOf(code);}
}

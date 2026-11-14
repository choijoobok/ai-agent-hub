// @feat FEAT-010
package com.aiagent.admin.chathelp;
import com.aiagent.admin.managed.*;
import com.aiagent.core.content.ChatHelpContent;
import com.aiagent.domain.message.service.MessageService;
import com.aiagent.support.content.SafeContentRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ChatHelpContentService implements ChatHelpContent {
    private final ManagedHistoryService history;private final ChatHelpQueryService queries;
    private final ChatHelpService owner;private final MessageService messages;
    public ChatHelpContentService(ManagedHistoryService history,ChatHelpQueryService queries,ChatHelpService owner,MessageService messages){this.history=history;this.queries=queries;this.owner=owner;this.messages=messages;}
    private ManagedHistoryService.Use use(Use value){if(value==null)throw ManagedErrorCode.FORBIDDEN.failure();return new ManagedHistoryService.Use(value.module(),value.kind(),value.key(),value.purpose());}
    @Transactional
    public Document current(Use use,String code,String language) {
        var current=queries.getOne(code,language);if(!current.state().equals("READY"))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        var ref=new ManagedHistoryStore.Ref(owner.kind(),code,current.revision());
        history.protect(use(use),java.util.List.of(ref));
        return exact(use,code,current.revision(),SafeContentRenderer.POLICY_VERSION,language);
    }
    @Transactional
    public Document exact(Use use,String code,String revision,String policyVersion,String language) {
        var kind=ChatHelpKind.require(code);
        if(!SafeContentRenderer.POLICY_VERSION.equals(policyVersion))throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        var event=history.readExact(use(use),new ManagedHistoryStore.Ref(owner.kind(),code,revision));
        var row=event.rows().get("AI_ADMIN_CHAT_HELP").getFirst();
        String format=row.get("FORMAT_CD").toString(),body=row.get("BODY").toString();
        var rendered=owner.preview(format,body);if(!rendered.hasContent())throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();
        return new Document(code,messages.text(kind.nameKey(),language),format,body,revision,Long.toString(event.historyNo()),rendered.policyVersion(),rendered.html(),rendered.removed());
    }
}

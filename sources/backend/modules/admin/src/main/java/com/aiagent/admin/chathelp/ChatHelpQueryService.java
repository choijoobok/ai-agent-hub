// @feat FEAT-010
package com.aiagent.admin.chathelp;
import com.aiagent.admin.managed.ManagedErrorCode;
import com.aiagent.domain.message.service.MessageService;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class ChatHelpQueryService {
    private static final PathBuilder<ChatHelp> H=new PathBuilder<>(ChatHelp.class,"chatHelp");
    public record View(String helpCd,String name,String position,String timing,String formatCd,String body,String revision,String state,
        Instant acceptedDtm,String changeReason,String modId,Instant modDtm){}
    private final JPAQueryFactory query;private final MessageService messages;
    public ChatHelpQueryService(JPAQueryFactory query,MessageService messages){this.query=query;this.messages=messages;}
    private View view(ChatHelp row,String language) {
        var kind=ChatHelpKind.require(row.helpCd);
        return new View(row.helpCd,messages.text(kind.nameKey(),language),messages.text(kind.positionKey(),language),messages.text(kind.timingKey(),language),
            row.formatCd,row.body,row.revision.toString(),row.body.isEmpty()?"UNSET":"READY",row.acceptedDtm,row.changeReason,row.modId,row.modDtm);
    }
    public List<View> getList(String language) {
        var rows=query.selectFrom(H).fetch();if(rows.size()!=3)throw ManagedErrorCode.UNAVAILABLE.failure();
        return Arrays.stream(ChatHelpKind.values()).map(k->rows.stream().filter(r->r.helpCd.equals(k.name())).findFirst().map(r->view(r,language)).orElseThrow(ManagedErrorCode.UNAVAILABLE::failure)).toList();
    }
    public View getOne(String helpCd,String language) {
        ChatHelpKind.require(helpCd);
        var row=query.selectFrom(H).where(H.getString("helpCd").eq(helpCd)).fetchOne();
        if(row==null)throw ManagedErrorCode.UNAVAILABLE.failure();return view(row,language);
    }
}

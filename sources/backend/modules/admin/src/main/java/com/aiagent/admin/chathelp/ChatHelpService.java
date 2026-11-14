// @feat FEAT-010
package com.aiagent.admin.chathelp;
import com.aiagent.admin.managed.*;
import com.aiagent.support.content.SafeContentRenderer;
import jakarta.persistence.EntityManager;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.transaction.annotation.*;
public class ChatHelpService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/chatHelp";
    private final DataSource source;
    private final EntityManager em;
    private final SafeContentRenderer renderer;
    private final TypedHistoryStore history;
    public ChatHelpService(DataSource source,EntityManager em,ManagedIds ids,SafeContentRenderer renderer) {
        this.source=source;this.em=em;this.renderer=renderer;
        history=new TypedHistoryStore(source,ids,kind(),"REVISION",List.of(new TypedHistoryStore.Table("AI_ADMIN_CHAT_HELP","HELP_CD",
            List.of("HELP_CD","FORMAT_CD","BODY","REVISION","ACCEPTED_DTM","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM"))),rows->true,rows->List.of(),ChatHelpKind.KEYS);
    }
    public DataSource dataSource(){return source;}
    public ManagedTargetKeys targetKeys(){return ChatHelpKind.KEYS;}
    public String feature(){return "FEAT-010";} public String kind(){return "CHAT_HELP";}
    public String readPath(){return BASE+"/getList";} public String writePath(){return BASE+"/save";}
    public Set<String> scopes(){return Set.of("content");}
    public Map<String,Object> normalize(ManagedCommand command) {
        if(command.targetId()==null||!command.values().keySet().equals(Set.of("formatCd","body"))
            ||!(command.values().get("formatCd") instanceof String format)||!(command.values().get("body") instanceof String body))throw ManagedErrorCode.VALIDATION.failure();
        if(!preview(format,body).hasContent())throw ManagedErrorCode.VALIDATION.failure();
        return command.values();
    }
    public SafeContentRenderer.Rendered preview(String format,String body) {
        try{return renderer.render(format,body);}catch(IllegalArgumentException failure){throw ManagedErrorCode.VALIDATION.failure();}
    }
    public List<ManagedLocks.Key> locks(ManagedCommand command){return List.of();}
    public Current current(ManagedCommand command) {
        var row=em.find(ChatHelp.class,(String)targetKeys().bind(command.targetId()));
        return row==null?null:new Current(row.helpCd,row.revision.toString());
    }
    public void validate(ManagedCommand command,Current before){}
    public boolean changed(ManagedCommand command,Current before) {
        var row=em.find(ChatHelp.class,before.targetId());
        return !row.formatCd.equals(command.values().get("formatCd"))||!row.body.equals(command.values().get("body"));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String apply(ManagedCommand command,Current before,Stamp stamp) {
        var row=em.find(ChatHelp.class,before.targetId());
        row.formatCd=(String)command.values().get("formatCd");row.body=(String)command.values().get("body");
        row.revision=Long.parseLong(stamp.revision());row.acceptedDtm=stamp.at();row.changeReason=stamp.reason();row.modId=stamp.actor();row.modDtm=stamp.at();
        em.flush();return row.helpCd;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void delete(ManagedCommand command,Current before){throw ManagedErrorCode.VALIDATION.failure();}
    public ManagedHistoryStore history(){return history;}
}

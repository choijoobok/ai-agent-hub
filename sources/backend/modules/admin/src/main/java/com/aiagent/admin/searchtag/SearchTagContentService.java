// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.admin.managed.*;
import com.aiagent.core.content.SearchTagContent;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SearchTagContentService implements SearchTagContent {
    private final SearchTagService owner;
    private final SearchTagQueryService queries;
    private final ManagedHistoryService history;
    private final List<ManagedHistoryService.Consumer> consumers;
    public SearchTagContentService(SearchTagService owner,SearchTagQueryService queries,ManagedHistoryService history,List<ManagedHistoryService.Consumer> consumers){this.owner=owner;this.queries=queries;this.history=history;this.consumers=List.copyOf(consumers);}
    private ManagedHistoryService.Use use(Use value){if(value==null)throw ManagedErrorCode.FORBIDDEN.failure();return new ManagedHistoryService.Use(value.module(),value.kind(),value.key(),value.purpose());}
    @Transactional
    public List<Document> current(Use use,Collection<String> permittedTagIds) {
        var consumer=use(use);
        consumers.stream().filter(source->source.module().equals(consumer.module())&&source.kind().equals(consumer.kind())).findFirst()
            .orElseThrow(()->ManagedErrorCode.FORBIDDEN.failure()).requireActive(consumer.key(),consumer.purpose());
        var ids=new TreeSet<>(permittedTagIds);owner.requireExisting(ids);
        var result=new ArrayList<Document>();
        for(String id:ids) {
            var tag=queries.getOne(id).tag();if(!tag.useYn().equals("Y"))continue;
            var ref=new ManagedHistoryStore.Ref(owner.kind(),id,tag.revision());history.protect(consumer,List.of(ref));
            result.add(exact(use,id,tag.revision()));
        }
        return List.copyOf(result);
    }
    @Transactional
    public Document exact(Use use,String tagId,String revision) {
        var event=history.readExact(use(use),new ManagedHistoryStore.Ref(owner.kind(),tagId,revision));
        var row=event.rows().get(SearchTagService.ROOT).getFirst();
        var synonyms=event.rows().get(SearchTagService.CHILD).stream().map(item->new Synonym(item.get("SYNONYM_ID").toString(),item.get("SEARCH_TEXT").toString(),item.get("NORMALIZED_TEXT").toString())).toList();
        return new Document(tagId,revision,Long.toString(event.historyNo()),"v1",row.get("SEARCH_TEXT").toString(),row.get("NORMALIZED_TEXT").toString(),row.get("USE_YN").toString(),synonyms);
    }
}

// @feat FEAT-024
package com.aiagent.admin.searchtag;
import jakarta.persistence.*;
@Entity @Table(name="AI_ADMIN_TAG_SYNONYM")
public class TagSynonym {
    @Id @Column(name="SYNONYM_ID") Long synonymId;
    @Column(name="TAG_ID",nullable=false) Long tagId;
    @Column(name="SEARCH_TEXT",nullable=false,length=200) String searchText;
    @Column(name="NORMALIZED_TEXT",nullable=false,length=400) String normalizedText;
    @Column(name="DESCRIPTION",columnDefinition="text") String description;
    protected TagSynonym() {}
}

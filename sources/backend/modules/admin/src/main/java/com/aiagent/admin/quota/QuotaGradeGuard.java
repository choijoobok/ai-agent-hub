// @feat FEAT-042
package com.aiagent.admin.quota;

import com.aiagent.admin.managed.*;
import com.aiagent.support.time.BusinessCalendar;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.*;
import org.springframework.dao.DataAccessException;

/** 미설치를 입증한 때만 후속 원장이 없는 기준 관리를 허용한다. 실패/일부 설치는 차단한다. */
public class QuotaGradeGuard {
    private final JdbcTemplate jdbc;
    private final BusinessCalendar calendar;
    private final ObjectProvider<QuotaGradeUsage> usages;
    public QuotaGradeGuard(DataSource source,BusinessCalendar calendar,ObjectProvider<QuotaGradeUsage> usages) {
        jdbc=new JdbcTemplate(source);this.calendar=calendar;this.usages=usages;
    }
    public Instant now(){return jdbc.queryForObject("SELECT clock_timestamp()",java.sql.Timestamp.class).toInstant();}
    public boolean future(String month,Instant now){return QuotaGradeDefinition.month(month).isAfter(YearMonth.from(now.atZone(calendar.zone())));}
    public void requireFuture(String month,Instant now){if(!future(month,now))throw ManagedErrorCode.REFERENCE_CONFLICT.failure();}
    public static ManagedLocks.Key boundary(){return new ManagedLocks.Key(0,"QUOTA_CALENDAR","1");}
    private QuotaGradeUsage usage() {
        var installed=usages.orderedStream().toList();if(installed.size()>1)throw ManagedErrorCode.UNAVAILABLE.failure();
        if(!installed.isEmpty())return installed.getFirst();
        boolean absent=Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection->{
            String schema=connection.getSchema();if(schema==null||schema.isBlank())return false;
            var tables=new HashSet<String>();
            try(var rows=connection.getMetaData().getTables(connection.getCatalog(),schema,null,null)) {
                while(rows.next())tables.add(rows.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
            }
            if(!tables.containsAll(Set.of("AI_ADMIN_QUOTA_GRADE_SET","AI_ADMIN_QUOTA_GRADE_SET_HIST","AI_ADMIN_QUOTA_GRADE_ITEM","AI_ADMIN_QUOTA_GRADE_ITEM_HIST")))return false;
            return tables.stream().noneMatch(name->name.startsWith("AI_ADMIN_USER_QUOTA_")||name.startsWith("AI_ADMIN_PORTAL_USER")
                    ||name.startsWith("AI_ADMIN_QUOTA_")&&!name.startsWith("AI_ADMIN_QUOTA_GRADE"));
        }));
        if(!absent)throw ManagedErrorCode.UNAVAILABLE.failure();return null;
    }
    public void validate(QuotaGradeUsage.Change change){var source=usage();if(source!=null)source.validate(change);}
    public List<ManagedLocks.Key> locks(QuotaGradeUsage.Change change){var source=usage();return source==null?List.of():source.locks(change);}
    public void apply(QuotaGradeUsage.Change change,ManagedAssetAdapter.Stamp stamp){var source=usage();if(source!=null)source.apply(change,stamp);}
    public QuotaGradeUsage.References references(Collection<String> ids) {
        try {
            var source=usage();if(source!=null)return source.references(ids);
            return new QuotaGradeUsage.References("NOT_INSTALLED",Map.of());
        } catch(DataAccessException|com.aiagent.core.error.BusinessException failure) {
            return new QuotaGradeUsage.References("UNAVAILABLE",Map.of());
        }
    }
}

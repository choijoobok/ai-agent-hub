// @feat FEAT-003
package com.aiagent.core.deployment;
import java.time.Instant;
import java.util.*;
/** 원천 값/비밀을 포함하지 않는 정확 공개 객체의 구조 관측. caller는 등록된 현재 인가 소비자다. */
public interface CatalogSource {
    record Names(String serverName,String databaseName,String schemaName,String objectName){}
    record Identity(String profile,Names names,String objectKey){}
    record Column(String name,int ordinalPosition,String sourceType,String formattedType,String nullable,
        Long characterLength,Long octetLength,Integer numericPrecision,Integer numericScale,String comment){}
    record Key(String kind,String name,List<String> columns){public Key{columns=List.copyOf(columns);}}
    record Index(String name,boolean unique,String method,List<String> keyExpressions,List<String> includedColumns,
        String predicate,String definition,boolean valid){public Index{keyExpressions=List.copyOf(keyExpressions);includedColumns=List.copyOf(includedColumns);}}
    record Observation(String state,String reason,Identity identity,String objectType,String objectToken,
        String productName,String productVersion,Instant observedAt,String settingsId,String bootId,
        List<Column> columns,List<Key> keys,List<Index> indexes){
        public Observation{columns=List.copyOf(columns);keys=List.copyOf(keys);indexes=List.copyOf(indexes);}
    }
    Identity declared(String dataSourceKey,Names names);
    Observation observe(String registeredCaller,String dataSourceKey,Names names);
}

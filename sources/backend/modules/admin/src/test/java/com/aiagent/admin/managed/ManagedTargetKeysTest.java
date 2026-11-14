// @feat FEAT-040 FEAT-010
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ManagedTargetKeysTest {
    @Test void decimalDefaultsStayStrict() {
        assertThat(ManagedTargetKeys.DECIMAL.bind("9007199254740993")).isEqualTo(9007199254740993L);
        for(String value:List.of("01","0","-1","USAGE_HELP","9223372036854775808"))
            assertThatThrownBy(()->ManagedTargetKeys.DECIMAL.bind(value));
        var owner=mock(ManagedAssetAdapter.class,CALLS_REAL_METHODS);
        assertThat(owner.targetKeys()).isSameAs(ManagedTargetKeys.DECIMAL);
    }
    @Test void fixedCodesAreExplicitAndImmutable() {
        var source=new HashSet<>(Set.of("USAGE_HELP","CHAT_START","INPUT_NOTICE"));
        var keys=ManagedTargetKeys.fixed(source);source.add("OTHER");
        assertThat(keys.bind("USAGE_HELP")).isEqualTo("USAGE_HELP");
        for(String value:List.of("OTHER","usage_help","1","USAGE_HELP ","' OR 1=1"))
            assertThatThrownBy(()->keys.bind(value));
        assertThatThrownBy(()->ManagedTargetKeys.fixed(Set.of()));
        assertThatThrownBy(()->ManagedTargetKeys.fixed(Set.of("a")));
    }
    @Test void envelopeAndOwnerValidationAreSeparate() {
        var command=new ManagedCommand(UUID.randomUUID().toString(),"USAGE_HELP","2","content",Map.of("body","원문"),"사유");
        var owner=mock(ManagedAssetAdapter.class,CALLS_REAL_METHODS);
        when(owner.scopes()).thenReturn(Set.of("content"));
        when(owner.normalize(command)).thenReturn(command.values());
        assertThatThrownBy(()->ManagedCommandService.normalize(owner,command));
        when(owner.targetKeys()).thenReturn(ManagedTargetKeys.fixed(Set.of("USAGE_HELP")));
        assertThat(ManagedCommandService.normalize(owner,command)).isEqualTo(command);
        assertThat(new ManagedHistoryStore.Ref("CHAT_HELP","USAGE_HELP","2").targetId()).isEqualTo("USAGE_HELP");
        assertThatThrownBy(()->new ManagedHistoryStore.Ref("CHAT_HELP","USAGE_HELP","01"));
    }
    @Test void invalidWireKeysNeverReachOwners() {
        for(String key:List.of("01","a","A".repeat(31),"A/B","A B",""))
            assertThatThrownBy(()->new ManagedCommand(UUID.randomUUID().toString(),key,"1","content",Map.of(),"사유"));
    }
}

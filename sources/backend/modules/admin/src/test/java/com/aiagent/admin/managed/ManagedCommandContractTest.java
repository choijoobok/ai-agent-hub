// @feat FEAT-040
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ManagedCommandContractTest {
    @Test @DisplayName("TC-0233 큰 정수/신규 키/범위와 원 명령의 엄격한 입력")
    void preservesDecimalIdentifiersAndRejectsInvalidInputs() {
        var command = new ManagedCommand(UUID.randomUUID().toString(), "9007199254740993", "2", "main", Map.of("name", "설명"), "변경 사유");
        assertThat(command.targetId()).isEqualTo("9007199254740993");
        assertThatThrownBy(() -> new ManagedCommand("bad", null, null, "main", Map.of(), "사유"));
        assertThatThrownBy(() -> new ManagedCommand(UUID.randomUUID().toString(), "01", "1", "main", Map.of(), "사유"));
        assertThatThrownBy(() -> new ManagedCommand(UUID.randomUUID().toString(), "1", null, "main", Map.of(), "사유"));
        assertThatThrownBy(() -> new ManagedCommand(UUID.randomUUID().toString(), null, null, "main", Map.of(), " "));
    }

    @Test @DisplayName("TC-0402 명령 지문은 순서와 무관하고 주체/누락/빈값은 구별한다")
    void digestIsCanonicalAndSeparatesActorAndAbsentValues() {
        var values = new LinkedHashMap<String,Object>(); values.put("z", "한글"); values.put("a", 1);
        var first = new ManagedCommand(UUID.randomUUID().toString(), null, null, "main", values, "사유");
        var same = new ManagedCommand(first.commandKey(), null, null, "main", Map.of("a", 1, "z", "한글"), "사유");
        assertThat(ManagedDigest.of("alice", "FEAT-006", "SAVE", first)).isEqualTo(ManagedDigest.of("alice", "FEAT-006", "SAVE", same));
        assertThat(ManagedDigest.of("bob", "FEAT-006", "SAVE", first)).isNotEqualTo(ManagedDigest.of("alice", "FEAT-006", "SAVE", first));
        var empty = new ManagedCommand(first.commandKey(), null, null, "main", Map.of("z", ""), "사유");
        assertThat(ManagedDigest.of("alice", "FEAT-006", "SAVE", empty)).isNotEqualTo(ManagedDigest.of("alice", "FEAT-006", "SAVE", first));
    }
}

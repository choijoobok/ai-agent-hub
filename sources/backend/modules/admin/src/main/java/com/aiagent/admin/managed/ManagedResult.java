// @feat FEAT-040
package com.aiagent.admin.managed;

import java.time.Instant;

public record ManagedResult(String commandKey,String targetId,String revision,String historyNo,
                            Instant appliedAt,String action,String outcome) {}

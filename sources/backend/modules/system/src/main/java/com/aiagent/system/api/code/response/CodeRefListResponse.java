// @feat FEAT-504
package com.aiagent.system.api.code.response;

import java.util.List;
public record CodeRefListResponse(long version,List<String> codes) {
    public CodeRefListResponse {codes=List.copyOf(codes);}
}

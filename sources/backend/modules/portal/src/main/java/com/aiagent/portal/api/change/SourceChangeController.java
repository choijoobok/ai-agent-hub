// @feat FEAT-305
package com.aiagent.portal.api.change;

import com.aiagent.api.error.ApiErrorWriter;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.portal.change.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SourceChangeController {
    private final SourceChangeModule module;private final SourceChangeService changes;private final ApiErrorWriter errors;
    public SourceChangeController(SourceChangeModule module,SourceChangeService changes,ApiErrorWriter errors){this.module=module;this.changes=changes;this.errors=errors;}
    @PostMapping(SourceChangeService.PATH)
    public ApiResponse<ChangeStore.Receipt> receiveChange(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException {
        response.setHeader("Cache-Control","no-store");
        try {
            var receipt=changes.receive(module.verified(request));
            if(receipt.outcome().equals("REJECTED")){response.setStatus(422);return new ApiResponse<>(false,receipt,com.aiagent.core.message.MsgKey.ERR_SOURCE_CHANGE,null,null,null,TraceId.current());}
            response.setStatus(receipt.notificationState().equals("PENDING_NO_RECIPIENT")?202:200);return ApiResponse.success(receipt,TraceId.current());
        }catch(ChangeFailure failure){response.setHeader("X-AIHUB-Change-Code",failure.code());errors.write(response,SourceChangeError.of(failure.status()));return null;}
    }
}

// @feat FEAT-504
package com.aiagent.system.api.code;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiagent.system.api.code.request.CodeRefListRequest;
import com.aiagent.domain.code.service.CodeService;
import com.aiagent.domain.code.service.CodeRefQueryService;
import com.aiagent.domain.message.entity.SysCacheVersion;
import com.aiagent.domain.message.service.CacheVersionService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class CodeRefContractTest {
    @Test void tc5046KeysAreRequiredButReferenceWhitespaceIsExact() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(var input:new CodeRefListRequest[]{new CodeRefListRequest(" ","P","A"),new CodeRefListRequest("G"," ","A"),new CodeRefListRequest("G","P",null),new CodeRefListRequest("G","P",""),new CodeRefListRequest("G","P","x".repeat(201))})assertThat(validator.validate(input)).isNotEmpty();
            var exact=new CodeRefListRequest("G","P"," ' value ' ");assertThat(validator.validate(exact)).isEmpty();assertThat(exact.refVal()).isEqualTo(" ' value ' ");
            assertThat(validator.validate(new CodeRefListRequest("G","P"," "))).isEmpty();
        }
    }
    @Test void tc5046VersionIsReadBeforeRowsAndFailuresAreNotEmptySuccess() {
        var codes=mock(CodeRefQueryService.class);var versions=mock(CacheVersionService.class);var controller=new CodeController(mock(CodeService.class),versions,codes);
        when(versions.current(SysCacheVersion.CODE)).thenReturn(7L);when(codes.codesByRef("G","P","A")).thenThrow(new IllegalStateException("시험 조회 실패"));
        assertThatThrownBy(()->controller.getListByRef(new CodeRefListRequest("G","P","A"))).isInstanceOf(IllegalStateException.class);
        var order=inOrder(versions,codes);order.verify(versions).current(SysCacheVersion.CODE);order.verify(codes).codesByRef("G","P","A");
    }
}

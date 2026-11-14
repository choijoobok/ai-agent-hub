// @feat FEAT-302
package com.aiagent.support.deployment;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ProtectedInputTest {
    @TempDir Path directory;
    @Test @DisplayName("TC-3023 운영 비밀 파일은 소유자 읽기만 허용·넓은 ACL/과대 입력 거부")
    void protectedFile()throws Exception {
        Path file=directory.resolve("synthetic-input");Files.writeString(file,"synthetic-private");
        var posix=Files.getFileAttributeView(file,PosixFileAttributeView.class);var acl=Files.getFileAttributeView(file,AclFileAttributeView.class);
        if(posix!=null)Files.setPosixFilePermissions(file,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        else acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        assertThat(ProtectedInput.read(file,100)).isEqualTo("synthetic-private");
        assertThatThrownBy(()->ProtectedInput.read(file,2)).isInstanceOf(SecretFailure.class).hasMessageNotContaining("synthetic-private");
        if(posix!=null)Files.setPosixFilePermissions(file,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.GROUP_READ));
        else {var entries=new ArrayList<>(acl.getAcl());entries.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName("BUILTIN\\Users")).setPermissions(AclEntryPermission.WRITE_DATA).build());acl.setAcl(entries);}
        assertThatThrownBy(()->ProtectedInput.read(file,100)).isInstanceOf(SecretFailure.class).hasMessageContaining("INPUT_PERMISSION");
    }
}

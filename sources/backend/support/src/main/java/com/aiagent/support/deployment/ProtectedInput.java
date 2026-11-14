// @feat FEAT-302
package com.aiagent.support.deployment;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 명시된 비밀 파일의 크기·일반 파일·읽기 권한을 확인한다. */
public final class ProtectedInput {
    private ProtectedInput(){}
    public static String read(Path path,int maxBytes) {
        try {
            if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maxBytes)throw new SecretFailure("INPUT_FILE",null);
            var posix=Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(posix!=null) {
                var permissions=posix.readAttributes().permissions();
                if(permissions.stream().anyMatch(p->p.name().startsWith("GROUP_")||p.name().startsWith("OTHERS_")))throw new SecretFailure("INPUT_PERMISSION",null);
            } else {
                var acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
                if(acl==null)throw new SecretFailure("INPUT_PERMISSION",null);
                String owner=acl.getOwner().getName();
                for(var entry:acl.getAcl())if(entry.type()==AclEntryType.ALLOW&&entry.permissions().stream().anyMatch(Set.of(AclEntryPermission.READ_DATA,AclEntryPermission.WRITE_DATA,AclEntryPermission.APPEND_DATA,AclEntryPermission.WRITE_ACL,AclEntryPermission.WRITE_OWNER,AclEntryPermission.DELETE)::contains)) {
                    String name=entry.principal().getName();
                    if(!name.equals(owner)&&!name.equalsIgnoreCase("NT AUTHORITY\\SYSTEM")&&!name.equalsIgnoreCase("BUILTIN\\Administrators"))throw new SecretFailure("INPUT_PERMISSION",null);
                }
            }
            byte[] bytes=Files.readAllBytes(path);if(bytes.length>maxBytes)throw new SecretFailure("INPUT_FILE",null);
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}finally{Arrays.fill(bytes,(byte)0);}
        }catch(SecretFailure failure){throw failure;}catch(Exception failure){throw new SecretFailure("INPUT_FILE",null);}
    }
}

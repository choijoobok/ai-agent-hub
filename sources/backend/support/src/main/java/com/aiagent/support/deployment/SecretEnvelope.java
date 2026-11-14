// @feat FEAT-302
package com.aiagent.support.deployment;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;

/** ADR-022 v1: Spring Crypto 원출력과 엄격한 용도 결합 봉투만 사용한다. */
public final class SecretEnvelope {
    private static final String PREFIX="ENC(v1.A256GCM.";
    public record Binding(String tenant,String environment,String property) {
        public Binding {if(tenant==null||!tenant.matches("[A-Za-z0-9_-]{1,100}")||environment==null||!environment.matches("[A-Za-z0-9_-]{1,100}")||!canonicalProperty(property))throw new SecretFailure("BINDING",property);}
    }
    private SecretEnvelope(){}
    public static boolean canonicalProperty(String name){return name!=null&&name.length()<=300&&name.matches("[a-z][a-z0-9-]*(?:\\[[0-9]+\\])?(?:\\.[a-z0-9][a-z0-9-]*(?:\\[[0-9]+\\])?)*");}
    private static byte[] decode(String value,int min,int max,String property) {
        try {
            if(value==null||value.length()>((max+2)/3)*4||!value.matches("[A-Za-z0-9_-]+"))throw new SecretFailure("FORMAT",property);
            byte[] bytes=Base64.getUrlDecoder().decode(value);
            if(bytes.length<min||bytes.length>max||!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value))throw new SecretFailure("FORMAT",property);
            return bytes;
        }catch(IllegalArgumentException failure){throw new SecretFailure("FORMAT",property);}
    }
    public static byte[] key(String value){return decode(value,32,32,"app.secrets.master-key");}
    private static List<String> context(Binding binding){return List.of("v1","A256GCM",binding.tenant(),binding.environment(),binding.property());}
    private static byte[] utf8(String value)throws CharacterCodingException {
        var bytes=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        byte[] result=new byte[bytes.remaining()];bytes.get(result);return result;
    }
    public static String encrypt(byte[] key,Binding binding,char[] secret) {
        byte[] plain=null;
        try {
            if(key.length!=32)throw new SecretFailure("KEY",binding.property());
            var fields=new ArrayList<>(context(binding));fields.add(new String(secret));var buffer=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(buffer)) {for(String field:fields){byte[] bytes=utf8(field);if(bytes.length>16384)throw new SecretFailure("SIZE",binding.property());out.writeInt(bytes.length);out.write(bytes);Arrays.fill(bytes,(byte)0);}}
            plain=buffer.toByteArray();byte[] cipher=AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(key,"AES")).build().encrypt(plain);
            if(cipher.length>65536)throw new SecretFailure("SIZE",binding.property());
            return PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(cipher)+")";
        }catch(SecretFailure failure){throw failure;}catch(Exception failure){throw new SecretFailure("ENCRYPT",binding.property());}
        finally {if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
    public static char[] decrypt(byte[] key,Binding binding,String text) {
        byte[] plain=null;
        try {
            if(key.length!=32||text==null||!text.startsWith(PREFIX)||!text.endsWith(")"))throw new SecretFailure("FORMAT",binding.property());
            byte[] cipher=decode(text.substring(PREFIX.length(),text.length()-1),32,65536,binding.property());
            plain=AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(key,"AES")).build().decrypt(cipher);
            try(var in=new DataInputStream(new ByteArrayInputStream(plain))) {
                var fields=new ArrayList<String>();
                for(int i=0;i<6;i++) {
                    int size=in.readInt();if(size<0||size>16384||size>in.available())throw new SecretFailure("PAYLOAD",binding.property());
                    fields.add(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(size))).toString());
                }
                if(in.available()!=0||!fields.subList(0,5).equals(context(binding)))throw new SecretFailure("BINDING",binding.property());
                return fields.get(5).toCharArray();
            }
        }catch(SecretFailure failure){throw failure;}catch(Exception failure){throw new SecretFailure("DECRYPT",binding.property());}
        finally {if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
}

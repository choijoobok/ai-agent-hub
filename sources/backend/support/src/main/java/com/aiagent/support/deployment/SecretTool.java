// @feat FEAT-302
package com.aiagent.support.deployment;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** 평문 인자를 받지 않고 암호문/안전한 검증 결과만 stdout에 출력하는 운영 도구. */
public final class SecretTool {
    private SecretTool(){}
    public static void main(String[] args){System.exit(run(args,System.in,System.out,System.err,System.getenv()));}
    static int run(String[] args,InputStream input,PrintStream output,PrintStream error,Map<String,String> env) {
        byte[] key=null,next=null;char[] secret=null;
        try {
            if(args.length<1||!Set.of("encrypt","verify","reencrypt").contains(args[0]))throw new SecretFailure("COMMAND",null);
            var options=new HashMap<String,String>();
            var allowed=Set.of("tenant","environment","property","key-file","key-env","new-key-file","new-key-env","input-file","secret-file","secret-stdin");
            for(int i=1;i<args.length;i++) {
                if(!args[i].startsWith("--")||!allowed.contains(args[i].substring(2)))throw new SecretFailure("OPTIONS",null);
                String name=args[i].substring(2),value=name.equals("secret-stdin")?"true":++i<args.length?args[i]:null;
                if(value==null||options.putIfAbsent(name,value)!=null)throw new SecretFailure("OPTIONS",null);
            }
            var binding=new SecretEnvelope.Binding(options.get("tenant"),options.get("environment"),options.get("property"));
            key=readKey(options,"",env);
            if(args[0].equals("encrypt")) {
                if(options.containsKey("input-file")||options.containsKey("new-key-file")||options.containsKey("new-key-env"))throw new SecretFailure("OPTIONS",null);
                if(options.containsKey("secret-stdin")==options.containsKey("secret-file"))throw new SecretFailure("SECRET_INPUT",null);
                secret=options.containsKey("secret-file")?ProtectedInput.read(Path.of(options.get("secret-file")),16384).toCharArray():read(input,16384).toCharArray();
                output.println(SecretEnvelope.encrypt(key,binding,secret));
            } else {
                if(options.containsKey("secret-stdin")||options.containsKey("secret-file"))throw new SecretFailure("OPTIONS",null);
                String cipher;
                if(options.containsKey("input-file"))try(var in=Files.newInputStream(Path.of(options.get("input-file")))){cipher=read(in,90000).strip();}else cipher=read(input,90000).strip();
                secret=SecretEnvelope.decrypt(key,binding,cipher);
                if(args[0].equals("verify")) {
                    if(options.containsKey("new-key-file")||options.containsKey("new-key-env"))throw new SecretFailure("OPTIONS",null);output.println("OK");
                }else{next=readKey(options,"new-",env);output.println(SecretEnvelope.encrypt(next,binding,secret));}
            }
            return 0;
        }catch(Exception failure){error.println(failure instanceof SecretFailure?failure.getMessage():"설정 비밀 도구 실패 [INPUT]");return 2;}
        finally {if(key!=null)Arrays.fill(key,(byte)0);if(next!=null)Arrays.fill(next,(byte)0);if(secret!=null)Arrays.fill(secret,'\0');}
    }
    private static byte[] readKey(Map<String,String> options,String prefix,Map<String,String> env) {
        String file=options.get(prefix+"key-file"),variable=options.get(prefix+"key-env");
        if(file!=null&&variable!=null)throw new SecretFailure("KEY_SOURCE",null);
        if(file!=null)return SecretEnvelope.key(ProtectedInput.read(Path.of(file),128).strip());
        if(variable==null){if(!prefix.isEmpty())throw new SecretFailure("NEW_KEY",null);variable=SecretEnvironment.MASTER;}
        if(!variable.matches("[A-Z][A-Z0-9_]{0,100}"))throw new SecretFailure("KEY_SOURCE",null);
        String value=env.get(variable);
        if(value==null&&System.console()!=null) {char[] chars=System.console().readPassword("환경 키: ");try{return SecretEnvelope.key(new String(chars));}finally{Arrays.fill(chars,'\0');}}
        return SecretEnvelope.key(value);
    }
    private static String read(InputStream input,int maximum)throws IOException {
        byte[] bytes=input.readNBytes(maximum+1);
        try {if(bytes.length>maximum)throw new SecretFailure("SIZE",null);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        finally{Arrays.fill(bytes,(byte)0);}
    }
}

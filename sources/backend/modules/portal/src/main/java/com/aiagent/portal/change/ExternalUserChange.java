// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.core.security.portal.PortalChangeOwner.Kind;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public record ExternalUserChange(String eventId,String subject,Kind kind,Instant occurredAt,String sourceVersion) {
    private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> FIELDS=Set.of("schemaVersion","sourceEventId","subject","kind","occurredAt","sourceVersion");
    public ExternalUserChange {text(eventId,200);text(subject,255);Objects.requireNonNull(kind);Objects.requireNonNull(occurredAt);text(sourceVersion,128);}
    public static ExternalUserChange parse(byte[] body) {
        if(body==null||body.length>16_384)throw new ChangeFailure(400,"INVALID_BODY");
        try {
            var node=JSON.readTree(body);
            if(!node.isObject()||!node.propertyNames().equals(FIELDS)||!node.path("schemaVersion").isIntegralNumber()||node.path("schemaVersion").intValue()!=1)throw new ChangeFailure(400,"INVALID_BODY");
            for(String key:FIELDS)if(!key.equals("schemaVersion")&&!node.get(key).isTextual())throw new ChangeFailure(400,"INVALID_BODY");
            return new ExternalUserChange(node.get("sourceEventId").asText(),node.get("subject").asText(),Kind.valueOf(node.get("kind").asText()),Instant.parse(node.get("occurredAt").asText()),node.get("sourceVersion").asText());
        } catch(RuntimeException failure){throw new ChangeFailure(400,"INVALID_BODY");}
    }
    static void text(String value,int limit){if(value==null||value.isBlank()||value.length()>limit||value.chars().anyMatch(Character::isISOControl))throw new ChangeFailure(400,"INVALID_BODY");}
    public String businessDigest(){return digest("change-body/v1",eventId,subject,kind.name(),occurredAt.toString(),sourceVersion);}
    public String eventKey(String namespace,String epoch){return digest("event/v1",namespace,epoch,eventId);}
    public static String digest(String... values) {
        try {var digest=MessageDigest.getInstance("SHA-256");for(String value:values){var bytes=value.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException failure){throw new IllegalStateException("SHA-256을 사용할 수 없다",failure);}
    }
    @Override public String toString(){return "ExternalUserChange[보호됨]";}
}

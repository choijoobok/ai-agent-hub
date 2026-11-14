// @feat FEAT-303
package com.aiagent.core.access;

import static com.aiagent.core.access.GroupAccess.*;
import static com.aiagent.core.access.CurrentDataAccess.*;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GroupAccessTest {
    private static final PrincipalRef USER=new PrincipalRef(AuthDomain.PORTAL,"1");
    private static final Target TABLE=new Target("CATALOG","10","content-1");
    private static final MaskRef LIGHT=new MaskRef("SYNTHETIC","v1","LIGHT"),HEAVY=new MaskRef("SYNTHETIC","v1","HEAVY");
    private final MaskingRegistry masks=new MaskingRegistry(List.of(
            new MaskingRegistry.Rule(LIGHT,1,Set.of(ValueType.STRING),v->"***"),
            new MaskingRegistry.Rule(HEAVY,2,Set.of(ValueType.STRING),v->"******")));
    private final GroupAccess evaluator=new GroupAccess(masks);
    private Request request(Set<String> fields){return new Request(USER,Purpose.RESULT,TABLE,fields,"trace-1");}
    private Gate table(Set<String> groups){return new Gate("table",TABLE,"policy-2",groups);}
    private Policy policy(List<Gate> gates,Map<String,Field> fields){return new Policy(TABLE,true,gates,fields,Set.of(),new Requirements(false,null,null,Set.of()));}
    private Snapshot snapshot(Policy policy,Set<String> groups){return new Snapshot(USER,true,"user-3",Instant.EPOCH,groups,policy,Map.of());}
    private Field field(Grant base,Map<String,Grant> exceptions){return new Field("table",ValueType.STRING,new MaskDomain("SYNTHETIC","v1"),base,exceptions);}
    @Test void unionsDoNotBypassIndependentGatesOrParentMembership() {
        var table=table(Set.of("A"));var api=new Gate("api",new Target("API","20","v1"),"p1",Set.of("B"));
        var p=policy(List.of(table,api),Map.of("name",field(Grant.raw(),Map.of())));
        assertThat(evaluator.evaluate(request(Set.of("name")),snapshot(p,Set.of("A","B"))).status()).isEqualTo(Status.ALLOW);
        assertThat(evaluator.evaluate(request(Set.of("name")),snapshot(p,Set.of("A"))).status()).isEqualTo(Status.DENY);
        assertThat(evaluator.evaluate(request(Set.of("name")),snapshot(p,Set.of("B"))).status()).isEqualTo(Status.DENY);
        var admin=new Request(new PrincipalRef(AuthDomain.ADMIN,"1"),Purpose.RESULT,TABLE,Set.of("name"),"trace-1");
        assertThat(evaluator.evaluate(admin,snapshot(p,Set.of("A","B"))).status()).isEqualTo(Status.DENY);
    }
    @Test void registeredInheritanceAndLeastRestrictiveCompatibleMask() {
        var p=policy(List.of(table(Set.of("A","B"))),Map.of(
                "name",field(Grant.masked(HEAVY),Map.of("B",Grant.masked(LIGHT))),
                "hidden",field(Grant.denied(),Map.of("B",Grant.raw())),
                "inherited",field(Grant.raw(),Map.of())));
        var d=evaluator.evaluate(request(p.fields().keySet()),snapshot(p,Set.of("A","B")));
        assertThat(d.fields().get("name").mask()).isEqualTo(LIGHT);
        assertThat(d.fields().get("hidden").mode()).isEqualTo(Mode.RAW);
        assertThat(d.fields().get("inherited").mode()).isEqualTo(Mode.RAW);
        assertThat(evaluator.evaluate(request(Set.of("unregistered")),snapshot(p,Set.of("A"))).status()).isEqualTo(Status.DENY);
        var invalid=policy(List.of(table(Set.of("A","B"))),Map.of("name",field(Grant.masked(HEAVY),Map.of("B",Grant.masked(new MaskRef("OTHER","v1","LIGHT"))))));
        assertThat(evaluator.evaluate(request(Set.of("name")),snapshot(invalid,Set.of("A","B"))).status()).isEqualTo(Status.UNAVAILABLE);
        assertThatThrownBy(()->new MaskingRegistry(List.of(new MaskingRegistry.Rule(LIGHT,1,Set.of(ValueType.STRING),v->"*"),new MaskingRegistry.Rule(HEAVY,1,Set.of(ValueType.STRING),v->"**")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownSourceAndMissingControlsNeverBecomeEmptyAllow() {
        var p=policy(List.of(table(Set.of())),Map.of());
        assertThat(evaluator.evaluate(request(Set.of()),snapshot(p,Set.of("A"))).status()).isEqualTo(Status.DENY);
        var incomplete=new Policy(TABLE,false,List.of(table(Set.of("A"))),Map.of(),Set.of(),new Requirements(false,null,null,Set.of()));
        assertThat(evaluator.evaluate(request(Set.of()),snapshot(incomplete,Set.of("A"))).status()).isEqualTo(Status.UNAVAILABLE);
        var required=new Policy(TABLE,true,List.of(table(Set.of("A"))),Map.of(),Set.of(),new Requirements(false,null,null,Set.of(Control.CONSENT)));
        assertThat(evaluator.evaluate(request(Set.of()),snapshot(required,Set.of("A"))).status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(evaluator.evaluate(request(Set.of()),new Snapshot(USER,true,"3",Instant.EPOCH,Set.of("A"),required,Map.of(Control.CONSENT,Check.DENIED))).status()).isEqualTo(Status.DENY);
    }
    @Test void referenceFieldsAndFieldMaskDomainCannotBeBypassedBySingleGroup() {
        var p=new Policy(TABLE,true,List.of(table(Set.of("A"))),Map.of("hidden",field(Grant.denied(),Map.of())),Set.of("hidden"),new Requirements(false,null,null,Set.of()));
        assertThat(evaluator.evaluate(request(Set.of()),snapshot(p,Set.of("A"))).status()).isEqualTo(Status.DENY);
        var incompatible=policy(List.of(table(Set.of("A"))),Map.of("name",new Field("table",ValueType.STRING,new MaskDomain("OTHER","v1"),Grant.masked(LIGHT),Map.of())));
        assertThat(evaluator.evaluate(request(Set.of("name")),snapshot(incompatible,Set.of("A"))).status()).isEqualTo(Status.UNAVAILABLE);
        var outside=policy(List.of(table(Set.of("A"))),Map.of("name",field(Grant.denied(),Map.of("B",Grant.raw()))));
        assertThat(evaluator.evaluate(request(Set.of("name")),snapshot(outside,Set.of("A","B"))).status()).isEqualTo(Status.UNAVAILABLE);
    }
    @Test void projectionIsBoundToCurrentUseAndNeverReturnsUnknownOrNestedRawValues() {
        var p=policy(List.of(table(Set.of("A"))),Map.of("name",field(Grant.masked(LIGHT),Map.of()),"secret",field(Grant.denied(),Map.of())));
        var request=request(p.fields().keySet());var d=evaluator.evaluate(request,snapshot(p,Set.of("A")));
        var retained=new AtomicReference<CurrentDataAccess.Use>();
        var result=evaluator.withSnapshot(request,snapshot(p,Set.of("A")),use->{
            retained.set(use);
            var safe=use.project(new Payload(d.binding(),List.of(Map.of("name","PRIVATE_NAME","secret","PRIVATE_SECRET"))));
            assertThat(safe.status()).isEqualTo(ProjectionStatus.CONTROLLED);
            assertThat(safe.rows()).containsExactly(Map.of("name","***"));
            assertThat(use.project(new Payload(d.binding(),List.of(Map.of("unknown","PRIVATE_RAW")))).rows()).isEmpty();
            assertThat(use.project(new Payload(d.binding(),List.of(Map.of("name",Map.of("nested","PRIVATE_RAW"))))).status()).isEqualTo(ProjectionStatus.MISMATCH);
            var other=new Binding(new Request(USER,Purpose.MODEL_INPUT,TABLE,request.fields(),"trace-1"),d.binding().policyRevisions(),d.binding().accountRevision());
            assertThat(use.project(new Payload(other,List.of(Map.of("name","PRIVATE_RAW")))).status()).isEqualTo(ProjectionStatus.MISMATCH);
            for(var different:List.of(
                    new Request(new PrincipalRef(AuthDomain.PORTAL,"2"),request.purpose(),TABLE,request.fields(),request.traceId()),
                    new Request(USER,request.purpose(),new Target("CATALOG","11","content-1"),request.fields(),request.traceId()),
                    new Request(USER,request.purpose(),new Target("CATALOG","10","content-2"),request.fields(),request.traceId())))
                assertThat(use.project(new Payload(new Binding(different,d.binding().policyRevisions(),d.binding().accountRevision()),List.of(Map.of("name","PRIVATE_RAW")))).rows()).isEmpty();
            return safe;
        });
        assertThat(result.toString()).doesNotContain("PRIVATE");
        assertThat(retained.get().project(new Payload(d.binding(),List.of(Map.of("name","PRIVATE_RAW")))).status()).isEqualTo(ProjectionStatus.MISMATCH);
    }
    @Test void transformationFailureAndCrossThreadUseNeverExposeRawPayload()throws Exception {
        var broken=new MaskingRegistry(List.of(new MaskingRegistry.Rule(LIGHT,1,Set.of(ValueType.STRING),v->{throw new IllegalStateException("PRIVATE_RAW");})));
        var p=policy(List.of(table(Set.of("A"))),Map.of("name",field(Grant.masked(LIGHT),Map.of())));
        var decision=new GroupAccess(broken).evaluate(request(Set.of("name")),snapshot(p,Set.of("A")));
        new GroupAccess(broken).withSnapshot(request(Set.of("name")),snapshot(p,Set.of("A")),use->{
            var input=new Payload(decision.binding(),List.of(Map.of("name","PRIVATE_RAW")));
            assertThat(input.toString()).doesNotContain("PRIVATE_RAW");
            assertThat(use.project(input).rows()).isEmpty();
            try(var worker=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(worker.submit(()->use.project(input)).get().status()).isEqualTo(ProjectionStatus.MISMATCH);
            } catch(Exception failure){throw new AssertionError(failure);}
            return null;
        });
    }
}

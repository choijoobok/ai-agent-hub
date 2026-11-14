// @feat FEAT-003
package com.aiagent.support.deployment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiagent.core.deployment.*;
import com.aiagent.support.time.BusinessCalendar;
import java.util.*;
import org.junit.jupiter.api.Test;
class PostgresCatalogSourceTest {
    @Test void exactNamesNeverFoldOrInferSchemaAndUtf8LengthIsChecked(){
        var a=PostgresCatalogSource.identity(new CatalogSource.Names(null,"db","sales","Orders"));
        assertThat(a.objectKey()).isNotEqualTo(PostgresCatalogSource.identity(new CatalogSource.Names(null,"db","sales","orders")).objectKey());
        assertThat(a.objectKey()).isNotEqualTo(PostgresCatalogSource.identity(new CatalogSource.Names(null,"db","salesOrders","Orders")).objectKey());
        assertThat(a.names().objectName()).isEqualTo("Orders");
        assertThatThrownBy(()->PostgresCatalogSource.identity(new CatalogSource.Names(null,"db",null,"orders"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->PostgresCatalogSource.identity(new CatalogSource.Names(null,"db","public","한".repeat(22)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->PostgresCatalogSource.identity(new CatalogSource.Names("server","db","public","orders"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void registryMustAuthorizeEveryObservationBeforeOpeningConnection(){
        var registry=mock(ConnectionRegistry.class);var loaded=mock(ExternalDataSources.class);var clock=mock(BusinessCalendar.class);var generation=UUID.randomUUID();
        when(loaded.entries()).thenReturn(Map.of("orders",new ExternalDataSources.Entry("orders","주문","POSTGRESQL","AVAILABLE",new ConnectionRegistry.Ref(ConnectionRegistry.Kind.DATASOURCE,"orders",generation))));
        when(loaded.bootId()).thenReturn(generation);
        when(registry.withResolvedConnection(anyString(),any(),any())).thenThrow(new ConnectionRegistry.Rejected(ConnectionRegistry.Status.FORBIDDEN));
        var result=new PostgresCatalogSource(loaded,registry,clock).observe("catalog-reader","orders",new CatalogSource.Names(null,"db","public","orders"));
        assertThat(result.state()).isEqualTo("ACCESS_DENIED");assertThat(result.columns()).isEmpty();assertThat(result.observedAt()).isNull();
        verify(registry).withResolvedConnection(eq("catalog-reader"),any(),any());
    }
}

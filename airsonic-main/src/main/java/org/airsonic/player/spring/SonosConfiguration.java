package org.airsonic.player.spring;

import org.airsonic.player.service.SonosService;
import org.airsonic.player.service.sonos.SonosFaultInterceptor;
import org.airsonic.player.service.sonos.SonosLinkSecurityInterceptor;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import java.util.Collections;


@Configuration
@ImportResource({"classpath:META-INF/cxf/cxf.xml", "classpath:META-INF/cxf/cxf-servlet.xml"})
public class SonosConfiguration {

    @Bean
    public EndpointImpl sonosEndpoint(Bus bus, SonosService sonosService,
            SonosFaultInterceptor sonosFaultInterceptor,
            SonosLinkSecurityInterceptor sonosSecurity) {
        EndpointImpl endpoint = new EndpointImpl(bus, sonosService);
        endpoint.setOutFaultInterceptors(Collections.singletonList(sonosFaultInterceptor));
        endpoint.setInInterceptors(Collections.singletonList(sonosSecurity));
        endpoint.publish("/Sonos");
        return endpoint;
    }
}

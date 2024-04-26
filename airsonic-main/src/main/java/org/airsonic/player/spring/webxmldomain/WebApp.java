package org.airsonic.player.spring.webxmldomain;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

@XmlRootElement(name = "web-app")
@XmlAccessorType(XmlAccessType.FIELD)
public class WebApp {
    @XmlElement(name = "servlet")
    private List<ServletDef> servletDefs;

    @XmlElement(name = "servlet-mapping")
    private List<ServletMappingDef> servletMappingDefs;

    public List<ServletDef> getServletDefs() {
        return servletDefs;
    }

    public void setServletDefs(List<ServletDef> servletDefs) {
        this.servletDefs = servletDefs;
    }

    public List<ServletMappingDef> getServletMappingDefs() {
        return servletMappingDefs;
    }

    public void setServletMappingDefs(List<ServletMappingDef> servletMappingDefs) {
        this.servletMappingDefs = servletMappingDefs;
    }
}

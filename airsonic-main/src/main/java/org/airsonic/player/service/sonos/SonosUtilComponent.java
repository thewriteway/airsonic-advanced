package org.airsonic.player.service.sonos;

import com.sonos.services._1.Credentials;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;



@Component
public class SonosUtilComponent {

    private Unmarshaller unmarshaller = createUnmarshaller();

    private Unmarshaller createUnmarshaller() {
        try {
            return JAXBContext.newInstance("com.sonos.services._1").createUnmarshaller();
        } catch (JAXBException e) {
            throw new AssertionError(e);
        }
    }

    public Credentials getCredentials(SoapMessage message) throws JAXBException {
        QName credentialQName = new QName("http://www.sonos.com/Services/1.1", "credentials");

        for (Header header : message.getHeaders()) {
            if (credentialQName.equals(header.getName())) {
                return unmarshaller.unmarshal((Node) header.getObject(), Credentials.class).getValue();
            }
        }

        return null;
    }

}

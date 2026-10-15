/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2015 (C) Sindre Mehus
 */

package org.airsonic.player.service.sonos;

import org.airsonic.player.config.HttpClientConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Registration with Sonos controller. They are 2 types of registration they are still supported by Sonos.
 * The ANONYMOUS and APPLICATION_LINK. The third USER_ID, must still work but will be not supported.</p>
 * <p></p>
 * @author Sindre Mehus
 * @author Nacrylic
 * @version $Id$
 */
@Component
public class SonosServiceRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SonosServiceRegistration.class);

    private final CloseableHttpClient httpClient;

    public SonosServiceRegistration(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * The type of Authentication fo Sonos, the old want USER_ID, is will be not supported. We must use the
     * Anonymous or AppLink. The USER_ID
     */
    public enum AuthenticationType {
        //@Deprecated use ANONYMOUS or APPLICATION_LINK
        @Deprecated
        DEVICE_LINK("DeviceLink"),
        @Deprecated
        USER_ID("UserId"),

        ANONYMOUS("Anonymous"),
        APPLICATION_LINK("AppLink");

        private String fieldValue;

        AuthenticationType(String fieldValue) {
            this.fieldValue = fieldValue;
        }

        public String getFieldValue() {
            return fieldValue;
        }
    }


    /**
     * Enable or disable Sonos registration
     *
     * @param airsonicBaseUrl must be the ip address, not the name
     * @param sonosControllerIp must be a ip too
     * @param enabled true for enable or false to disable
     * @param sonosServiceName the name of service you will see on Sonos service list
     * @param sonosServiceId the ID, the free id is : 240-253 or 255
     * @throws IOException if some io problem
     */
    public boolean setEnabled(String airsonicBaseUrl, String sonosControllerIp, boolean enabled, String sonosServiceName,
                           int sonosServiceId, AuthenticationType authenticationType) throws IOException {
        String localUrl = airsonicBaseUrl + "ws/Sonos";
        String controllerUrl = String.format("http://%s:1400/customsd", sonosControllerIp);

        LOG.info("Setting Sonos music service enabled={}, using Sonos controller IP={}, SID={}, Airsonic url={}", enabled, sonosControllerIp, sonosServiceId, localUrl);

        List<Pair<String, String>> params = new ArrayList<>();
        params.add(Pair.of("sid", String.valueOf(sonosServiceId)));

        // Need the csrf token on each request
        String csrfToken = retrieveCsrfToken(controllerUrl);
        if (csrfToken != null) {
            params.add(Pair.of("csrfToken", csrfToken));
        }

        if (enabled) {
            params.add(Pair.of("name", sonosServiceName));
            params.add(Pair.of("uri", localUrl));
            params.add(Pair.of("secureUri", localUrl));
            params.add(Pair.of("pollInterval", "1200"));
            params.add(Pair.of("containerType", "MService"));
            params.add(Pair.of("caps", "search"));
            params.add(Pair.of("caps", "trFavorites"));
            params.add(Pair.of("caps", "alFavorites"));
            params.add(Pair.of("caps", "ucPlaylists"));
            params.add(Pair.of("caps", "extendedMD"));

            // If you change airsonic/airsonic-main/src/main/webapp/sonos/presentationMap.xml
            // Change the presentationMapVersion @see https://musicpartners.sonos.com/node/134
            params.add(Pair.of("presentationMapVersion", "1"));
            params.add(Pair.of("presentationMapUri", airsonicBaseUrl + "sonos/presentationMap.xml"));

            // Don't forget to change `stringsVersion` if you change the text in airsonic/airsonic-main/src/main/webapp/sonos/strings.xml
            // Change the stringsVersion @see https://musicpartners.sonos.com/node/134
            params.add(Pair.of("stringsVersion", "11"));
            params.add(Pair.of("stringsUri", airsonicBaseUrl + "sonos/strings.xml"));
            params.add(Pair.of("authType", authenticationType.getFieldValue()));

        } else {

            // To disable a Sonos device, just name it with an empty value.
            params.add(Pair.of("name", null));
        }

        return execute(controllerUrl, params);
    }


    private boolean execute(String url, List<Pair<String, String>> parameters) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        for (Pair<String, String> parameter : parameters) {
            params.add(new BasicNameValuePair(parameter.getKey(), parameter.getValue()));
        }

        HttpPost request = new HttpPost(url);
        request.setConfig(HttpClientConfig.responseTimeout(Timeout.ofSeconds(20)));

        // We're not using UTF8 encoding here since the Sonos controller doesn't like it,
        // and the Content-Type carries no charset parameter.
        request.setEntity(new StringEntity(WWWFormCodec.format(params, StandardCharsets.ISO_8859_1),
                ContentType.create("application/x-www-form-urlencoded")));

        String result = httpClient.execute(request, new BasicHttpClientResponseHandler());
        LOG.info("Sonos controller returned: {}", result);

        return result.contains("Success");
    }

    private String retrieveCsrfToken(String controllerUrl) throws IOException {
        Document doc = Jsoup.connect(controllerUrl).get();
        Element element = doc.selectFirst("input[name='csrfToken']");

        if (element != null) {
            return element.attributes().get("value");
        }

        return null;
    }
}

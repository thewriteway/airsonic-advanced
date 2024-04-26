package org.airsonic.player.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "airsonic.cue")
public class AirsonicCueConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AirsonicCueConfig.class);

    // properties
    private boolean enabled;
    private boolean hideIndexedFiles;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHideIndexedFiles() {
        return enabled && hideIndexedFiles;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setHideIndexedFiles(boolean hideIndexedFiles) {
        LOG.warn("deprecated property 'airsonic.cue.hide-indexed-files'. Use 'airsonic.hide-virtual-tracks' instead.");
        this.hideIndexedFiles = hideIndexedFiles;
    }
}

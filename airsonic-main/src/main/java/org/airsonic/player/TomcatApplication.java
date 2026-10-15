package org.airsonic.player;

import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

public class TomcatApplication {

    public static void configure(TomcatServletWebServerFactory tomcatFactory) {

        tomcatFactory.addContextCustomizers(context -> {

            // No JSPs are used, so skip scanning jars for tag library descriptors
            StandardJarScanFilter standardJarScanFilter = new StandardJarScanFilter();
            standardJarScanFilter.setTldSkip("*");
            context.getJarScanner().setJarScanFilter(standardJarScanFilter);

            boolean development = (System.getProperty("airsonic.development") != null);

            // Increase the size and time before eviction of the Tomcat
            // cache so that resources aren't uncompressed too often.
            // See https://github.com/jhipster/generator-jhipster/issues/3995

            StandardRoot resources = new StandardRoot();
            if (development) {
                resources.setCachingAllowed(false);
            } else {
                resources.setCacheMaxSize(100000);
                resources.setCacheObjectMaxSize(4000);
                resources.setCacheTtl(24 * 3600 * 1000);  // 1 day, in milliseconds
            }
            context.setResources(resources);
        });
    }
}

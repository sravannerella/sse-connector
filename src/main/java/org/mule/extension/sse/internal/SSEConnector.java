package org.mule.extension.sse.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import org.mule.sdk.api.meta.JavaVersion;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;


@Extension(name = "SSE", vendor = "K2 Partnering Solutions")
@JavaVersionSupport({JavaVersion.JAVA_17})
@Configurations(SSEConfiguration.class)
@Xml(prefix = "sse")
public class SSEConnector {

    public SSEConnector() {
        // Extension initialization if needed
    }
    
}

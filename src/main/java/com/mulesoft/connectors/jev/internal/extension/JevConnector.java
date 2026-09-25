package com.mulesoft.connectors.jev.internal.extension;

import org.mule.sdk.api.annotation.Configurations;
import org.mule.sdk.api.annotation.Extension;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import org.mule.sdk.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.error.ErrorTypes;
import org.mule.sdk.api.meta.Category;

import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;

import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

/**
 * Jev Connector extension entry point. Jev is a decision model: given a state and named typed questions (Noul, Choice,
 * Score) it returns one typed answer per question. This connector turns those answers into first-class Mule values.
 */
@Extension(name = "Jev", category = Category.SELECT)
@JavaVersionSupport(JAVA_17)
@Xml(prefix = "jev", namespace = "http://www.mulesoft.org/schema/mule/jev")
@Configurations(JevConfiguration.class)
@ErrorTypes(JevErrorType.class)
public class JevConnector {
}

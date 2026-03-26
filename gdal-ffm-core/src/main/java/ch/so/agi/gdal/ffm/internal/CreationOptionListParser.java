package ch.so.agi.gdal.ffm.internal;

import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class CreationOptionListParser {
    private CreationOptionListParser() {
    }

    static List<String> enumValues(String creationOptionListXml, String optionName) {
        Objects.requireNonNull(optionName, "optionName must not be null");
        String normalizedOptionName = optionName.trim();
        if (normalizedOptionName.isEmpty() || creationOptionListXml == null || creationOptionListXml.isBlank()) {
            return List.of();
        }

        Document document = parse(creationOptionListXml);
        if (document == null) {
            return List.of();
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        NodeList optionNodes = document.getElementsByTagName("Option");
        for (int i = 0; i < optionNodes.getLength(); i++) {
            Node optionNode = optionNodes.item(i);
            if (!(optionNode instanceof Element optionElement)) {
                continue;
            }
            if (!normalizedOptionName.equalsIgnoreCase(optionElement.getAttribute("name"))) {
                continue;
            }

            NodeList valueNodes = optionElement.getElementsByTagName("Value");
            for (int valueIndex = 0; valueIndex < valueNodes.getLength(); valueIndex++) {
                Node valueNode = valueNodes.item(valueIndex);
                if (!(valueNode instanceof Element valueElement)) {
                    continue;
                }

                String value = valueElement.getTextContent();
                if (value == null || value.isBlank()) {
                    value = valueElement.getAttribute("value");
                }
                if (value != null) {
                    String normalized = value.trim();
                    if (!normalized.isEmpty()) {
                        values.add(normalized);
                    }
                }
            }
        }
        return List.copyOf(values);
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException ignored) {
            // Use default parser behavior for unsupported features.
        }
    }
}

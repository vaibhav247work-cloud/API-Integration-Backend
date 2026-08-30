package com.example.integration.service;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import com.jayway.jsonpath.JsonPath;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PathExtractorService {

    public String extractString(String body, PayloadFormat format, String path, PathType pathType) {
        if (!StringUtils.hasText(body) || !StringUtils.hasText(path)) {
            return null;
        }

        PathType effectivePathType = pathType != null ? pathType : defaultPathType(format);
        try {
            if (effectivePathType == PathType.JSON_PATH) {
                Object result = JsonPath.read(body, path);
                return stringify(result);
            }

            Document document = parseXml(body);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String result = (String) xPath.evaluate(path, document, XPathConstants.STRING);
            return StringUtils.hasText(result) ? result.trim() : null;
        } catch (Exception ex) {
            throw new IntegrationFailureException(
                    FailureCategory.RESPONSE_PARSING_ERROR,
                    "Failed to extract path [" + path + "] from response",
                    "RESPONSE_EXTRACTION",
                    null,
                    null,
                    truncate(body),
                    false,
                    ex);
        }
    }

    public Integer extractInteger(String body, PayloadFormat format, String path, PathType pathType) {
        String value = extractString(body, format, path, pathType);
        return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : null;
    }

    public Document parseXml(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IntegrationFailureException(
                    FailureCategory.RESPONSE_PARSING_ERROR,
                    "Failed to parse XML response",
                    "RESPONSE_EXTRACTION",
                    null,
                    null,
                    truncate(body),
                    false,
                    ex);
        }
    }

    private PathType defaultPathType(PayloadFormat format) {
        return format == PayloadFormat.JSON ? PathType.JSON_PATH : PathType.XPATH;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : stringify(list.get(0));
        }
        return String.valueOf(value);
    }

    private String truncate(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return body.length() <= 1000 ? body : body.substring(0, 1000);
    }
}

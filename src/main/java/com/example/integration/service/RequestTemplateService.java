package com.example.integration.service;

import com.example.integration.model.runtime.ExecutionContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RequestTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    public String resolve(String template, ExecutionContext context) {
        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = context.getVariableAsString(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public Map<String, String> resolveMap(Map<String, String> source, ExecutionContext context) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return resolved;
        }
        source.forEach((key, value) -> resolved.put(resolve(key, context), resolve(value, context)));
        return resolved;
    }
}

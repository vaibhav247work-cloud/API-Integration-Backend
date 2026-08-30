package com.example.integration.service;

import com.example.integration.model.enums.PathType;
import com.example.integration.model.runtime.ExecutionContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class MappingExpressionService {

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public String evaluate(
            String expression,
            RecordAccessor recordAccessor,
            Map<String, String> currentRow,
            ExecutionContext context) {

        if (!StringUtils.hasText(expression)) {
            return null;
        }

        StandardEvaluationContext evaluationContext = new StandardEvaluationContext(
                new ExpressionFunctions(recordAccessor, currentRow, context));
        Object value = expressionParser.parseExpression(expression).getValue(evaluationContext);
        return value == null ? null : String.valueOf(value);
    }

    public interface RecordAccessor {
        String value(String path, PathType pathType);

        BigDecimal number(String path, PathType pathType);
    }

    public static class ExpressionFunctions {

        private final RecordAccessor recordAccessor;
        private final Map<String, String> currentRow;
        private final ExecutionContext context;

        public ExpressionFunctions(
                RecordAccessor recordAccessor,
                Map<String, String> currentRow,
                ExecutionContext context) {
            this.recordAccessor = recordAccessor;
            this.currentRow = currentRow;
            this.context = context;
        }

        public String value(String path) {
            return recordAccessor.value(path, null);
        }

        public BigDecimal num(String path) {
            return recordAccessor.number(path, null);
        }

        public String column(String header) {
            return currentRow.get(header);
        }

        public BigDecimal columnNum(String header) {
            return toBigDecimal(currentRow.get(header));
        }

        public String ctx(String key) {
            return context == null ? null : context.getVariableAsString(key);
        }

        public BigDecimal ctxNum(String key) {
            return context == null ? BigDecimal.ZERO : toBigDecimal(context.getVariableAsString(key));
        }

        private BigDecimal toBigDecimal(String value) {
            if (!StringUtils.hasText(value)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value.trim());
        }
    }
}

package com.kleinkauf.observability.collector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
Reads the Prometheus text format (version 0.0.4) returned by /metrics:
  name{label="value",...} 12.5
Comment lines (# HELP, # TYPE) are skipped and so are the ones that cannot be parsed: an odd
metric must not bring down the reading of the others.
 */
public final class PrometheusText {

    public record Sample(String name, Map<String, String> labels, double value) {

        public String label(String key) {
            return labels.get(key);
        }
    }

    private PrometheusText() {
    }

    public static List<Sample> parse(String text) {
        List<Sample> samples = new ArrayList<>();
        if (text == null) {
            return samples;
        }
        for (String line : text.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Sample sample = parseLine(line);
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
    }

    private static Sample parseLine(String line) {
        int brace = line.indexOf('{');
        int space = line.indexOf(' ');
        if (space < 0) {
            return null;
        }
        String name;
        Map<String, String> labels = new LinkedHashMap<>();
        String rest;
        if (brace >= 0 && brace < space) {
            name = line.substring(0, brace);
            int end = parseLabels(line, brace + 1, labels);
            if (end < 0) {
                return null;
            }
            rest = line.substring(end + 1).strip();
        } else {
            name = line.substring(0, space);
            rest = line.substring(space + 1).strip();
        }
        // A timestamp may follow the value
        String value = rest.split("\\s+")[0];
        Double parsed = parseValue(value);
        return parsed == null ? null : new Sample(name, labels, parsed);
    }

    // Returns the position of the closing '}', or -1 if the line is malformed
    private static int parseLabels(String line, int start, Map<String, String> labels) {
        int i = start;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '}') {
                return i;
            }
            if (c == ',' || c == ' ') {
                i++;
                continue;
            }
            int eq = line.indexOf('=', i);
            if (eq < 0 || eq + 1 >= line.length() || line.charAt(eq + 1) != '"') {
                return -1;
            }
            String key = line.substring(i, eq).strip();
            StringBuilder value = new StringBuilder();
            int j = eq + 2;
            boolean closed = false;
            while (j < line.length()) {
                char v = line.charAt(j);
                if (v == '\\' && j + 1 < line.length()) {
                    char next = line.charAt(j + 1);
                    value.append(next == 'n' ? '\n' : next);
                    j += 2;
                    continue;
                }
                if (v == '"') {
                    closed = true;
                    break;
                }
                value.append(v);
                j++;
            }
            if (!closed) {
                return -1;
            }
            labels.put(key, value.toString());
            i = j + 1;
        }
        return -1;
    }

    static Double parseValue(String value) {
        return switch (value) {
            case "+Inf", "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            default -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }
}

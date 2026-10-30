package com.resumatch.ats;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects standard sections in normalized resume text.
 * Returns a SectionMap with present flags and a structured map of section content.
 */
public final class SectionDetector {
    private static final Map<String, List<Pattern>> HEADERS = new HashMap<>();
    static {
        HEADERS.put("contact", List.of(
                Pattern.compile("\\bcontact(?:\\s+info(?:rmation)?)?\\b"),
                Pattern.compile("\\bpersonal\\s+details?\\b")
        ));
        HEADERS.put("summary", List.of(
                Pattern.compile("\\b(summary|objective|profile|about\\s+me)\\b")
        ));
        HEADERS.put("skills", List.of(
                Pattern.compile("\\b(skills?|technical\\s+skills?|core\\s+competencies)\\b"),
                Pattern.compile("\\btechnologies\\b")
        ));
        HEADERS.put("experience", List.of(
                Pattern.compile("\\b(work\\s+experience|experience|employment|professional\\s+experience|internship)\\b")
        ));
        HEADERS.put("education", List.of(
                Pattern.compile("\\b(education|academic|qualifications?)\\b")
        ));
        HEADERS.put("projects", List.of(
                Pattern.compile("\\b(projects?|academic\\s+projects?|personal\\s+projects?)\\b")
        ));
        HEADERS.put("certifications", List.of(
                Pattern.compile("\\b(certifications?|licenses?|achievements?)\\b")
        ));
    }

    private SectionDetector() {}

    public static SectionMap detect(String text) {
        SectionMap map = new SectionMap();
        if (text == null || text.isBlank()) return map;
        String[] lines = text.split("\\R");
        String current = "header";
        StringBuilder currentBuf = new StringBuilder();
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        for (String l : lines) {
            String stripped = l.trim();
            String lower = stripped.toLowerCase();
            String matched = null;
            for (var e : HEADERS.entrySet()) {
                for (Pattern p : e.getValue()) {
                    if (p.matcher(lower).find() && stripped.length() < 60) {
                        matched = e.getKey();
                        break;
                    }
                }
                if (matched != null) break;
            }
            if (matched != null) {
                sections.putIfAbsent(current, currentBuf);
                current = matched;
                currentBuf = sections.computeIfAbsent(current, k -> new StringBuilder());
                map.found.add(matched);
            } else {
                currentBuf.append(stripped).append('\n');
            }
        }
        sections.putIfAbsent(current, currentBuf);
        map.sections = sections;
        // contact: present if email or phone detected
        map.hasEmail = TextNormalizer.extractEmail(text).isPresent();
        map.hasPhone = TextNormalizer.extractPhone(text).isPresent();
        return map;
    }

    public static class SectionMap {
        public final Set<String> found = new HashSet<>();
        public Map<String, StringBuilder> sections = new LinkedHashMap<>();
        public boolean hasEmail;
        public boolean hasPhone;

        public String textOf(String key) {
            StringBuilder sb = sections.get(key);
            return sb == null ? "" : sb.toString();
        }
    }
}

package com.resumatch.ats;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Normalization utilities for the rule-based ATS engine.
 * All matchers and scoring components flow through this class.
 */
public final class TextNormalizer {
    private static final Pattern PUNCT = Pattern.compile("[\\p{Punct}]+");
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9+#./ ]");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s\\-]{8,}\\d)");
    private static final Pattern YEARS = Pattern.compile("(\\d{4})\\s*[-–to]+\\s*(\\d{4}|present|current|now)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    private TextNormalizer() {}

    public static String normalize(String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase();
        s = PUNCT.matcher(s).replaceAll(" ");
        s = WS.matcher(s).replaceAll(" ").trim();
        return s;
    }

    public static String skillKey(String s) {
        if (s == null) return "";
        String raw = s.trim().toLowerCase();
        // language-specific canonicalization BEFORE punctuation strip
        // so we don't conflate c, c++, c#, c sharp into the same key.
        if (raw.equals("c++") || raw.equals("cpp") || raw.equals("c plus plus")) return "cpp";
        if (raw.equals("c#") || raw.equals("csharp") || raw.equals("c sharp")) return "csharp";
        if (raw.equals("f#") || raw.equals("fsharp") || raw.equals("f sharp")) return "fsharp";
        String n = normalize(s);
        n = n.replaceAll("^[.#/ ]+|[.#/ ]+$", "");
        n = WS.matcher(n).replaceAll(" ").trim();
        return n;
    }

    public static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String n = normalize(text);
        if (n.isEmpty()) return Set.of();
        String[] parts = NON_WORD.matcher(n).replaceAll(" ").split(" ");
        return Arrays.stream(parts).filter(t -> t.length() > 1).collect(Collectors.toCollection(HashSet::new));
    }

    public static Set<String> bigrams(Set<String> tokens) {
        List<String> sorted = new ArrayList<>(tokens);
        Collections.sort(sorted);
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 1 < sorted.size(); i++) {
            out.add(sorted.get(i) + " " + sorted.get(i + 1));
        }
        return out;
    }

    public static Optional<String> extractEmail(String text) {
        if (text == null) return Optional.empty();
        var m = EMAIL.matcher(text);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    public static Optional<String> extractPhone(String text) {
        if (text == null) return Optional.empty();
        var m = PHONE.matcher(text);
        if (m.find()) {
            String p = m.group().replaceAll("[\\s\\-]", "");
            if (p.length() >= 10) return Optional.of(p);
        }
        return Optional.empty();
    }

    public static Optional<String> extractName(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String firstLine = text.split("\\R", 2)[0].trim();
        if (firstLine.isEmpty()) return Optional.empty();
        // heuristic: 2-4 words, mostly alphabetic, possibly capitals
        String[] parts = firstLine.split("\\s+");
        if (parts.length < 2 || parts.length > 4) return Optional.empty();
        for (String p : parts) {
            if (!p.matches("[A-Za-z][A-Za-z.'-]{1,30}")) return Optional.empty();
        }
        return Optional.of(firstLine);
    }

    /**
     * Estimate years of experience from date ranges like "2021-2023" or "2021 - present".
     */
    public static double estimateYears(String text) {
        if (text == null) return 0.0;
        Set<String> seen = new HashSet<>();
        double total = 0.0;
        var m = YEARS.matcher(text);
        while (m.find()) {
            String a = m.group(1);
            String b = m.group(2);
            int from = Integer.parseInt(a);
            int to;
            if (b.matches("\\d{4}")) to = Integer.parseInt(b);
            else to = java.time.Year.now().getValue();
            String key = a + "-" + to;
            if (seen.add(key)) total += Math.max(0, to - from);
        }
        if (total == 0.0) {
            // fall back: count of unique years as a soft signal
            var sm = SINGLE_YEAR.matcher(text);
            Set<String> ys = new HashSet<>();
            while (sm.find()) ys.add(sm.group());
            if (ys.size() >= 3) total = 1.0;
        }
        return total;
    }
}

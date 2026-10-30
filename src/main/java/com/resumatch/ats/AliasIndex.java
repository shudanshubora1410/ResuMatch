package com.resumatch.ats;

import java.util.*;

/**
 * In-memory alias index built once at startup from skills + skill_aliases tables.
 * Each alias maps to a canonical skill id and a normalized name.
 */
public class AliasIndex {
    private final Map<String, Long> aliasToSkillId = new HashMap<>();
    private final Map<Long, String> skillIdToName = new HashMap<>();
    private final Map<Long, Set<String>> skillIdToAliases = new HashMap<>();
    private final List<String> sortedAliases = new ArrayList<>();

    public void load(List<AliasRow> rows) {
        aliasToSkillId.clear();
        skillIdToName.clear();
        skillIdToAliases.clear();
        for (AliasRow r : rows) {
            aliasToSkillId.put(r.normalizedAlias, r.skillId);
            skillIdToName.putIfAbsent(r.skillId, r.canonicalName);
            skillIdToAliases.computeIfAbsent(r.skillId, k -> new HashSet<>()).add(r.normalizedAlias);
        }
        sortedAliases.clear();
        sortedAliases.addAll(aliasToSkillId.keySet());
        // longest first so multi-word skills are matched greedily
        sortedAliases.sort((a, b) -> Integer.compare(b.length(), a.length()));
    }

    public boolean isEmpty() { return aliasToSkillId.isEmpty(); }

    public List<String> sortedAliases() { return sortedAliases; }

    /** Returns the set of distinct canonical names in the dictionary. */
    public Set<String> canonicalNames() { return new HashSet<>(skillIdToName.values()); }

    public Set<String> skillIdToAliases(long id) {
        return skillIdToAliases.getOrDefault(id, Set.of());
    }

    public String canonicalName(long id) {
        return skillIdToName.getOrDefault(id, "");
    }

    /**
     * Scan resume text and return the set of skill ids that are mentioned.
     * Greedy longest-match scan so "spring boot" is preferred over "spring".
     */
    public SkillScan scan(String text) {
        String norm = TextNormalizer.normalize(text);
        SkillScan scan = new SkillScan();
        // mask out matched spans to prevent overlapping. We process aliases in
        // length-descending order, but to ensure shorter aliases don't sneak in
        // inside a longer match (e.g. "java" inside "javascript") we keep a single
        // monotonic `from` cursor across all aliases. The cursor is the earliest
        // index that hasn't been claimed by a previous match.
        char[] mask = norm.toCharArray();
        int cursor = 0;
        for (String alias : sortedAliases) {
            // start searching at the current cursor; but a shorter alias could
            // appear AFTER a longer alias match, so we also need to scan the
            // whole rest of the text — just skip spans that are already masked.
            int from = 0;
            while (true) {
                int idx = norm.indexOf(alias, from);
                if (idx < 0) break;
                // only count this match if it starts at or after the cursor AND
                // the character at idx hasn't been masked by a previous longer match
                if (idx < cursor || mask[idx] == ' ') {
                    from = idx + 1;
                    continue;
                }
                // require whole-word-ish boundaries (space or boundary)
                boolean leftOk = idx == 0 || mask[idx - 1] == ' ';
                boolean rightOk = (idx + alias.length() == mask.length) || mask[idx + alias.length()] == ' ';
                if (leftOk && rightOk) {
                    for (int k = idx; k < idx + alias.length(); k++) {
                        if (mask[k] != ' ') mask[k] = ' ';
                    }
                    // advance the cursor past this match
                    cursor = Math.max(cursor, idx + alias.length());
                    Long skillId = aliasToSkillId.get(alias);
                    if (skillId != null) {
                        scan.skillIds.add(skillId);
                        scan.canonicalNames.add(skillIdToName.get(skillId));
                    }
                }
                from = idx + Math.max(1, alias.length());
            }
        }
        return scan;
    }

    /** Same logic as scan but applied to a synthetic set of hypothetical skills. */
    public SkillScan scanHypothetical(List<String> hypothetical) {
        SkillScan scan = new SkillScan();
        if (hypothetical == null) return scan;
        for (String s : hypothetical) {
            String key = TextNormalizer.skillKey(s);
            Long skillId = aliasToSkillId.get(key);
            if (skillId != null) {
                scan.skillIds.add(skillId);
                scan.canonicalNames.add(skillIdToName.get(skillId));
            } else {
                // accept as raw token if unknown
                scan.canonicalNames.add(s.trim());
            }
        }
        return scan;
    }

    public static class SkillScan {
        public final Set<Long> skillIds = new HashSet<>();
        public final Set<String> canonicalNames = new HashSet<>();
        public int size() { return skillIds.size(); }
    }

    public static class AliasRow {
        public final long skillId;
        public final String canonicalName;
        public final String normalizedAlias;
        public AliasRow(Long skillId, String canonicalName, String normalizedAlias) {
            this.skillId = skillId == null ? 0L : skillId;
            this.canonicalName = canonicalName;
            this.normalizedAlias = normalizedAlias;
        }
    }
}

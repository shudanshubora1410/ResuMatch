package com.resumatch.common;

import com.resumatch.ats.TextNormalizer;
import com.resumatch.skills.AliasIndexService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Tiny public endpoint that powers the "Try the formula" widget on the
 * landing page. Anyone can hit it — no auth needed — to compute a general
 * ATS score from a list of pasted skills.
 *
 * Intentionally a separate, lighter endpoint than the full resume upload:
 * no file parsing, no format checks, no PDF report. Just: "you said you know
 * X, Y, Z — here's what the engine would say about your resume."
 */
@RestController
@RequestMapping("/api/public/score-demo")
public class ScoringDemoController {

    private final AliasIndexService aliasIndexService;

    public ScoringDemoController(AliasIndexService aliasIndexService) {
        this.aliasIndexService = aliasIndexService;
    }

    @PostMapping
    public Map<String, Object> compute(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) body.getOrDefault("skills", List.of());

        // Per-input scan. We use the strict alias index (not scanHypothetical) so
        // unknown skills surface to the visitor — a teaching moment about how
        // aliasing works. (The previous version had a dead code block above this
        // that masked the bug where "java" matched inside "javascript" — that bug
        // is now fixed in AliasIndex.scan itself and a regression test covers it.)
        Set<String> resolved = new LinkedHashSet<>();
        Set<String> unknown = new TreeSet<>();
        for (String s : skills) {
            if (s == null || s.isBlank()) continue;
            var single = aliasIndexService.get().scan(s);
            if (!single.canonicalNames.isEmpty()) {
                resolved.addAll(single.canonicalNames);
            } else {
                unknown.add(s.trim());
            }
        }

        int n = resolved.size();
        // Project a general score out of 100. We don't have format/structure/etc
        // info from this widget, so we assume max on those (20 + 15 + 20) and
        // give full experience (20) if the candidate has 1+ matched skills.
        // The real engine would compute lower numbers for a real resume.
        double skillsOutOf25 = Math.min(n, 14) / 14.0 * 25.0;
        double total = skillsOutOf25 + 20 + 15 + 20;
        double exp = n > 0 ? 20 : 0;
        total += exp;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalScore", Math.round(total * 10.0) / 10.0);
        out.put("skillsScore", Math.round(skillsOutOf25 * 10.0) / 10.0);
        out.put("matchedSkills", resolved);
        out.put("unknownSkills", unknown);
        out.put("verdict", total >= 85 ? "Strong" : total >= 70 ? "Good" : total >= 50 ? "Mixed" : "Needs work");
        out.put("explanation", "Projected general score if a resume mentions " + resolved.size() +
                " canonical skills out of the " + aliasIndexService.get().canonicalNames().size() + " in our dictionary.");
        return out;
    }
}

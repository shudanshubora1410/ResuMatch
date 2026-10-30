package com.resumatch.ats;

import com.resumatch.common.Severity;
import com.resumatch.resume.ResumeFormatFlagEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based formatting checks. Returns flag rows ready for persistence.
 */
public final class FormattingChecker {
    private FormattingChecker() {}

    public static List<ResumeFormatFlagEntity> check(long versionId, String text, SectionDetector.SectionMap sm) {
        List<ResumeFormatFlagEntity> flags = new ArrayList<>();
        if (!sm.hasEmail) flags.add(flag(versionId, "MISSING_EMAIL", Severity.HIGH, "contact",
                "No email address detected in the resume."));
        if (!sm.hasPhone) flags.add(flag(versionId, "MISSING_PHONE", Severity.MEDIUM, "contact",
                "No phone number detected in the resume."));

        if (!sm.found.contains("skills")) flags.add(flag(versionId, "MISSING_SKILLS_SECTION", Severity.HIGH, "skills",
                "No dedicated skills section was detected."));
        if (!sm.found.contains("experience") && !sm.found.contains("projects"))
            flags.add(flag(versionId, "MISSING_EXPERIENCE_OR_PROJECTS", Severity.MEDIUM, "experience",
                "Neither experience nor projects sections were found."));

        if (!sm.found.contains("education")) flags.add(flag(versionId, "MISSING_EDUCATION", Severity.MEDIUM, "education",
                "No education section was detected."));

        if (text != null) {
            int len = text.length();
            if (len < 800) flags.add(flag(versionId, "TEXT_TOO_SHORT", Severity.HIGH, "general",
                    "Extracted text is short (" + len + " chars). PDF may be image-heavy or scanned."));
            if (len > 12000) flags.add(flag(versionId, "TEXT_VERY_LONG", Severity.LOW, "general",
                    "Resume is unusually long; consider trimming to 1-2 pages."));
            if (text.matches("(?s).*[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F].*"))
                flags.add(flag(versionId, "CONTROL_CHARS", Severity.LOW, "general",
                        "Unusual control characters found in the file."));
            if (text.split("\\R").length < 5)
                flags.add(flag(versionId, "TOO_FEW_LINES", Severity.MEDIUM, "general",
                        "Fewer than 5 lines of text were extracted."));
        }
        return flags;
    }

    private static ResumeFormatFlagEntity flag(long versionId, String type, Severity sev, String section, String msg) {
        ResumeFormatFlagEntity f = new ResumeFormatFlagEntity();
        f.resumeVersionId = versionId;
        f.flagType = type;
        f.severity = sev;
        f.sectionName = section;
        f.message = msg;
        return f;
    }
}

package com.resumatch.ats;

/**
 * Centralized, documented scoring weights for the rule-based ATS engine.
 * Change here, never inline.
 */
public final class AtsWeights {
    private AtsWeights() {}

    // ----- General ATS (out of 100) -----
    public static final double GENERAL_FORMATTING = 20;
    public static final double GENERAL_COMPLETENESS = 15;
    public static final double GENERAL_STRUCTURE = 20;
    public static final double GENERAL_SKILLS = 25;
    public static final double GENERAL_EXPERIENCE = 20;
    public static final double GENERAL_TOTAL = GENERAL_FORMATTING + GENERAL_COMPLETENESS
            + GENERAL_STRUCTURE + GENERAL_SKILLS + GENERAL_EXPERIENCE;

    // ----- Company-specific ATS (out of 100) -----
    public static final double COMPANY_MUST_HAVE = 50;
    public static final double COMPANY_NICE_TO_HAVE = 20;
    public static final double COMPANY_EXPERIENCE = 15;
    public static final double COMPANY_EDUCATION = 5;
    public static final double COMPANY_FORMATTING = 10;
    public static final double COMPANY_TOTAL = COMPANY_MUST_HAVE + COMPANY_NICE_TO_HAVE
            + COMPANY_EXPERIENCE + COMPANY_EDUCATION + COMPANY_FORMATTING;
}

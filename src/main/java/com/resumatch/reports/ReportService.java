package com.resumatch.reports;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.ats.CompanyScoreEntity;
import com.resumatch.ats.CompanyScoreRepository;
import com.resumatch.ats.GeneralScoreEntity;
import com.resumatch.ats.GeneralScoreRepository;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.resume.ResumeExtractionRepository;
import com.resumatch.resume.ResumeFormatFlagRepository;
import com.resumatch.resume.ResumeVersionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ReportService {

    // Editorial palette — printed report aesthetic
    private static final PDColor INK         = new PDColor(new float[]{0.08f, 0.07f, 0.06f}, PDDeviceRGB.INSTANCE);
    private static final PDColor INK_SOFT    = new PDColor(new float[]{0.30f, 0.27f, 0.23f}, PDDeviceRGB.INSTANCE);
    private static final PDColor INK_MUTED   = new PDColor(new float[]{0.55f, 0.50f, 0.45f}, PDDeviceRGB.INSTANCE);
    private static final PDColor RULE        = new PDColor(new float[]{0.82f, 0.79f, 0.72f}, PDDeviceRGB.INSTANCE);
    private static final PDColor ACCENT      = new PDColor(new float[]{0.10f, 0.23f, 0.18f}, PDDeviceRGB.INSTANCE);
    private static final PDColor ACCENT_BG   = new PDColor(new float[]{0.94f, 0.96f, 0.93f}, PDDeviceRGB.INSTANCE);
    private static final PDColor PAPER       = new PDColor(new float[]{1.0f, 0.98f, 0.97f}, PDDeviceRGB.INSTANCE);
    private static final PDColor FLAG_HI_BG  = new PDColor(new float[]{1.0f, 0.95f, 0.93f}, PDDeviceRGB.INSTANCE);
    private static final PDColor FLAG_MD_BG  = new PDColor(new float[]{1.0f, 0.98f, 0.93f}, PDDeviceRGB.INSTANCE);

    private final ResumeVersionRepository versionRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final ResumeFormatFlagRepository flagRepo;
    private final GeneralScoreRepository generalScoreRepo;
    private final ApplicationRepository appRepo;
    private final JobPostingRepository jobRepo;
    private final CompanyScoreRepository companyScoreRepo;

    public ReportService(ResumeVersionRepository versionRepo, ResumeExtractionRepository extractionRepo,
                         ResumeFormatFlagRepository flagRepo, GeneralScoreRepository generalScoreRepo,
                         ApplicationRepository appRepo, JobPostingRepository jobRepo,
                         CompanyScoreRepository companyScoreRepo) {
        this.versionRepo = versionRepo;
        this.extractionRepo = extractionRepo;
        this.flagRepo = flagRepo;
        this.generalScoreRepo = generalScoreRepo;
        this.appRepo = appRepo;
        this.jobRepo = jobRepo;
        this.companyScoreRepo = companyScoreRepo;
    }

    public byte[] generateForVersion(Long seekerId, Long versionId) {
        var v = versionRepo.findById(versionId).orElseThrow(() -> new java.util.NoSuchElementException("Version not found"));
        var ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(versionId).orElse(null);
        var flags = flagRepo.findByResumeVersionId(versionId);
        var gs = generalScoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(versionId).orElse(null);
        var myApps = appRepo.findBySeekerIdOrderByAppliedAtDesc(seekerId);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float pw = page.getMediaBox().getWidth();
            float ph = page.getMediaBox().getHeight();
            float m = 56f; // generous margins, like a printed report

            // paper background
            drawRect(cs, 0, 0, pw, ph, PAPER);

            // masthead — minimal, like a journal header
            float y = ph - 60;
            drawText(cs, "ResuMatch", m, y, 11, PDType1Font.HELVETICA_BOLD, INK);
            drawText(cs, "ATS REPORT", m + 90, y, 9, PDType1Font.HELVETICA, INK_MUTED);
            // date and version on the right
            String date = java.time.LocalDate.now().toString();
            drawText(cs, date, pw - m - fontWidth(date, 9, PDType1Font.HELVETICA), y, 9, PDType1Font.HELVETICA, INK_MUTED);
            y -= 8;
            drawLine(cs, m, y, pw - m, y, RULE, 0.5f);
            y -= 36;

            // candidate
            String name = ex == null ? "Candidate" : safe(ex.extractedName);
            drawText(cs, name, m, y, 24, PDType1Font.HELVETICA_BOLD, INK);
            y -= 16;
            String contact = (ex == null ? "" : safe(ex.extractedEmail)) +
                    ((ex != null && ex.extractedPhone != null) ? "   ·   " + safe(ex.extractedPhone) : "");
            drawText(cs, contact, m, y, 9, PDType1Font.HELVETICA, INK_SOFT);
            y -= 14;
            String meta = "v" + v.versionNumber + "  ·  " + safe(v.versionLabel) + "  ·  " +
                    (ex == null ? 0 : round(ex.experienceYears == null ? 0.0 : ex.experienceYears)) + " years experience  ·  " +
                    java.time.Instant.ofEpochMilli(v.uploadedAt.toEpochMilli()).toString().substring(0, 10);
            drawText(cs, meta, m, y, 9, PDType1Font.HELVETICA, INK_MUTED);
            y -= 28;
            drawLine(cs, m, y, pw - m, y, RULE, 0.5f);
            y -= 28;

            // THE SCORE — typographic, no ring
            drawLabel(cs, m, y, "GENERAL ATS SCORE");
            y -= 30;
            double total = gs == null ? 0 : gs.totalScore;
            // big serif-style number — Helvetica Bold mimics display weight in PDF
            drawText(cs, String.valueOf((int) Math.round(total)), m, y, 80, PDType1Font.HELVETICA_BOLD, ACCENT);
            drawText(cs, "/ 100", m + 130, y, 20, PDType1Font.HELVETICA, INK_MUTED);
            // verdict to the right
            String verdict = total >= 85 ? "Strong" : total >= 70 ? "Good" : total >= 50 ? "Mixed" : "Weak";
            drawText(cs, verdict, m + 230, y + 30, 16, PDType1Font.HELVETICA_BOLD, INK);
            y -= 36;
            drawText(cs, scoreInterpretation(total), m, y, 9, PDType1Font.HELVETICA, INK_SOFT);
            y -= 32;

            // sub-scores as a clean two-column table
            if (gs != null) {
                drawLine(cs, m, y, pw - m, y, RULE, 0.3f);
                y -= 18;
                drawLabel(cs, m, y, "SUB-SCORES");
                y -= 22;
                float colW = (pw - 2 * m) / 2 - 12;
                float col1x = m;
                float col2x = m + colW + 24;
                drawSubScore(cs, col1x, y, colW, "Formatting",    gs.formattingScore,   20);
                drawSubScore(cs, col2x, y, colW, "Completeness",  gs.completenessScore, 15);
                y -= 26;
                drawSubScore(cs, col1x, y, colW, "Structure",     gs.structureScore,    20);
                drawSubScore(cs, col2x, y, colW, "Skills",        gs.skillsScore,       25);
                y -= 26;
                drawSubScore(cs, col1x, y, colW, "Experience",    gs.experienceScore,   20);
                y -= 36;
            }

            // flags
            drawLine(cs, m, y, pw - m, y, RULE, 0.3f);
            y -= 18;
            drawLabel(cs, m, y, "FORMATTING NOTES");
            y -= 18;
            if (flags.isEmpty()) {
                drawText(cs, "No formatting issues detected. Clean structure.", m, y, 9, PDType1Font.HELVETICA, INK_SOFT);
                y -= 16;
            } else {
                int shown = 0;
                for (var f : flags) {
                    if (y < 100) break;
                    if (shown >= 5) break;
                    drawText(cs, "[" + f.severity + "]  " + safe(f.sectionName) + "  —  " + trunc(safe(f.message), 90),
                            m, y, 9, PDType1Font.HELVETICA, INK);
                    y -= 14;
                    shown++;
                }
                if (flags.size() > 5) {
                    drawText(cs, "+ " + (flags.size() - 5) + " more flag(s)", m, y, 9, PDType1Font.HELVETICA, INK_MUTED);
                    y -= 14;
                }
            }
            y -= 14;

            // company-specific scores
            if (y < 180) y = 180; // keep table on page if possible
            drawLine(cs, m, y, pw - m, y, RULE, 0.3f);
            y -= 18;
            drawLabel(cs, m, y, "COMPANY-SPECIFIC SCORES");
            y -= 18;
            int compShown = 0;
            for (var a : myApps) {
                if (y < 80) break;
                if (compShown >= 5) break;
                if (!a.resumeVersionId.equals(versionId)) continue;
                var job = jobRepo.findById(a.jobId).orElse(null);
                var score = companyScoreRepo.findFirstByApplicationIdOrderByComputedAtDesc(a.id).orElse(null);
                if (score == null) continue;
                String title = safe(job == null ? "(job)" : job.title);
                drawText(cs, title, m, y, 11, PDType1Font.HELVETICA_BOLD, INK);
                String breakdown = "Must " + round(score.mustHaveScore) + "  ·  Nice " + round(score.niceToHaveScore) +
                        "  ·  Exp " + round(score.experienceScore) + "  ·  Edu " + round(score.educationScore) +
                        "  ·  Fmt " + round(score.formattingScore);
                drawText(cs, breakdown, m, y - 13, 8, PDType1Font.HELVETICA, INK_MUTED);
                String tot = round(score.totalScore) + " / 100";
                drawText(cs, tot, pw - m - fontWidth(tot, 14, PDType1Font.HELVETICA_BOLD), y, 14, PDType1Font.HELVETICA_BOLD, ACCENT);
                y -= 32;
                compShown++;
            }
            if (compShown == 0) {
                drawText(cs, "No applications from this resume version yet.", m, y, 9, PDType1Font.HELVETICA, INK_MUTED);
                y -= 14;
            }

            // footer
            drawLine(cs, m, 50, pw - m, 50, RULE, 0.3f);
            drawText(cs, "ResuMatch  ·  rule-based, explainable ATS scoring", m, 36, 8, PDType1Font.HELVETICA, INK_MUTED);
            drawText(cs, "Page 1 of 1", pw - m - 50, 36, 8, PDType1Font.HELVETICA, INK_MUTED);

            cs.close();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private String scoreInterpretation(double t) {
        if (t >= 85) return "Resume reads cleanly. Sub-scores are within healthy ranges.";
        if (t >= 70) return "Resume is solid with room to improve in a few areas below.";
        if (t >= 50) return "Mixed result — some sections are strong, others need work.";
        return "Significant issues detected. See formatting notes and recommendations.";
    }

    private void drawSubScore(PDPageContentStream cs, float x, float y, float w, String label, double score, double max) throws Exception {
        drawText(cs, label, x, y, 9, PDType1Font.HELVETICA, INK_SOFT);
        String val = round(score) + " / " + (int) max;
        drawText(cs, val, x + w - fontWidth(val, 9, PDType1Font.HELVETICA_BOLD), y, 9, PDType1Font.HELVETICA_BOLD, INK);
        // thin progress line
        drawLine(cs, x, y - 4, x + w, y - 4, RULE, 0.3f);
        double pct = max <= 0 ? 0 : Math.max(0, Math.min(1, score / max));
        drawLine(cs, x, y - 4, x + (float) (w * pct), y - 4, ACCENT, 1.2f);
    }

    // ---- drawing helpers ----
    private void drawRect(PDPageContentStream cs, float x, float y, float w, float h, PDColor c) {
        try { cs.setNonStrokingColor(c); cs.addRect(x, y, w, h); cs.fill(); } catch (Exception ignored) {}
    }
    private void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2, PDColor c, float w) {
        try { cs.setStrokingColor(c); cs.setLineWidth(w); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke(); } catch (Exception ignored) {}
    }
    private void drawText(PDPageContentStream cs, String text, float x, float y, float size, PDFont font, PDColor color) {
        try {
            cs.setNonStrokingColor(color);
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(text == null ? "" : text);
            cs.endText();
        } catch (Exception ignored) {}
    }
    private void drawLabel(PDPageContentStream cs, float x, float y, String text) {
        drawText(cs, text, x, y, 8, PDType1Font.HELVETICA_BOLD, INK_MUTED);
    }
    private float fontWidth(String s, float size, PDFont font) {
        try { return font.getStringWidth(s) / 1000f * size; } catch (Exception e) { return s.length() * size * 0.55f; }
    }
    private String safe(String s) { return s == null ? "" : s; }
    private double round(double v) { return Math.round(v * 10.0) / 10.0; }
    private String trunc(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n - 1) + "." : s); }
}

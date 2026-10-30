package com.resumatch.parsing;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

@Service
public class ResumeParser {

    public ParsedResult parse(Path filePath, String mimeType, String originalName) {
        String lower = originalName.toLowerCase();
        try {
            if (mimeType != null && mimeType.equals("application/pdf") || lower.endsWith(".pdf")) {
                return parsePdf(filePath);
            }
            if (lower.endsWith(".docx") || (mimeType != null && mimeType.contains("wordprocessingml"))) {
                return parseDocx(filePath);
            }
            if (lower.endsWith(".doc")) {
                // legacy .doc not supported; return empty text and a clear signal
                ParsedResult r = new ParsedResult();
                r.text = "";
                r.qualityScore = 0.0;
                r.error = "Legacy .doc format is not supported. Please upload PDF or DOCX.";
                return r;
            }
            if (lower.endsWith(".txt")) {
                ParsedResult r = new ParsedResult();
                r.text = new String(java.nio.file.Files.readAllBytes(filePath));
                r.qualityScore = 0.7;
                return r;
            }
            ParsedResult r = new ParsedResult();
            r.text = "";
            r.qualityScore = 0.0;
            r.error = "Unsupported file type. Please upload PDF or DOCX.";
            return r;
        } catch (Exception e) {
            ParsedResult r = new ParsedResult();
            r.text = "";
            r.qualityScore = 0.0;
            r.error = "Failed to extract text: " + e.getMessage();
            return r;
        }
    }

    private ParsedResult parsePdf(Path filePath) throws Exception {
        try (PDDocument doc = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            ParsedResult r = new ParsedResult();
            r.text = text;
            r.qualityScore = text.strip().length() > 400 ? 0.9 : 0.4;
            return r;
        }
    }

    private ParsedResult parseDocx(Path filePath) throws Exception {
        try (InputStream is = new FileInputStream(filePath.toFile());
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            String text = ex.getText();
            ParsedResult r = new ParsedResult();
            r.text = text;
            r.qualityScore = text.strip().length() > 400 ? 0.85 : 0.4;
            return r;
        }
    }

    public static class ParsedResult {
        public String text = "";
        public double qualityScore = 0.0;
        public String error;
    }
}

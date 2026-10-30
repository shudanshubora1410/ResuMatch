package com.resumatch.ats;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    @Test
    void normalizesCaseAndPunctuation() {
        assertEquals("java spring boot", TextNormalizer.normalize("Java, Spring-Boot!"));
    }

    @Test
    void skillKeyHandlesCPlusPlus() {
        assertEquals("cpp", TextNormalizer.skillKey("C++"));
        assertEquals("cpp", TextNormalizer.skillKey("cpp"));
        assertEquals("cpp", TextNormalizer.skillKey("C Plus Plus"));
        assertEquals("csharp", TextNormalizer.skillKey("C#"));
        assertEquals("csharp", TextNormalizer.skillKey("CSharp"));
        assertEquals("csharp", TextNormalizer.skillKey("C Sharp"));
        assertEquals("java", TextNormalizer.skillKey("  Java  "));
    }

    @Test
    void extractsEmail() {
        Optional<String> e = TextNormalizer.extractEmail("Contact me at john.doe@example.com for details.");
        assertTrue(e.isPresent());
        assertEquals("john.doe@example.com", e.get());
    }

    @Test
    void extractsPhone() {
        Optional<String> p = TextNormalizer.extractPhone("Phone: +91 98765 12345");
        assertTrue(p.isPresent());
        assertEquals("+919876512345", p.get());
    }

    @Test
    void estimatesYearsFromRanges() {
        String t = "Acme 2021-2023\nBeta 2023-present";
        assertEquals(5.0, TextNormalizer.estimateYears(t), 0.01);
    }

    @Test
    void aliasIndexDoesNotMatchShorterSubstring() {
        // Regression: scanning "JavaScript" used to also match "Java" because the
        // loop reset `from` to 0 for each alias. The mask was correct but `indexOf`
        // runs on the unmodified `norm` string, so the next shorter alias re-found
        // the substring at index 0 and the boundary check (which reads the mask)
        // passed because the previous match had set position 4 to a space.
        AliasIndex idx = new AliasIndex();
        idx.load(java.util.List.of(
                new AliasIndex.AliasRow(1L, "Java", "java"),
                new AliasIndex.AliasRow(2L, "JavaScript", "javascript")
        ));
        var s = idx.scan("JavaScript");
        assertEquals(1, s.canonicalNames.size(), "Java should NOT match inside JavaScript");
        assertTrue(s.canonicalNames.contains("JavaScript"));
    }
}

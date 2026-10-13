package com.khedma.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Loose matching for Arabic personal names (typed differently on a sign-up form and in the roster). */
public final class ArabicNames {

    private ArabicNames() {}

    /** Lower-cases, strips diacritics and tatweel, folds common letter variants and collapses spaces. */
    public static String normalize(String name) {
        if (name == null) return "";
        String s = name.toLowerCase(Locale.ROOT)
                .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return s;
    }

    /**
     * 0..1 closeness of two names: shared words over all words (so an extra middle name costs little),
     * with a bonus when the first names match and a full match scoring 1.
     */
    public static double similarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0;
        if (na.equals(nb)) return 1;
        List<String> wa = Arrays.asList(na.split(" "));
        List<String> wb = Arrays.asList(nb.split(" "));
        Set<String> shared = new HashSet<>(wa);
        shared.retainAll(wb);
        if (shared.isEmpty()) return 0;
        Set<String> all = new HashSet<>(wa);
        all.addAll(wb);
        double jaccard = (double) shared.size() / all.size();
        double firstNameBonus = wa.get(0).equals(wb.get(0)) ? 0.25 : 0;
        return Math.min(0.99, jaccard * 0.75 + firstNameBonus);
    }
}

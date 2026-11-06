package com.example.fluxora;

import java.util.Locale;
import java.util.regex.Pattern;

public class RelevanceRules {

    private static final String[] WORDS = {
            "cancel", "postpone", "reschedul", "extra class", "makeup", "make-up", "make up",
            "no class", "class off", "assignment", "submit", "submission", "deadline",
            "quiz", "class test", "exam", "viva", "presentation", "syllabus",
            "will be held", "held on", "room", "venue", "section", "sec ", "lab ",
            "বাতিল", "স্থগিত", "ক্লাস", "অ্যাসাইনমেন্ট", "এসাইনমেন্ট", "জমা", "ডেডলাইন",
            "কুইজ", "পরীক্ষা", "ভাইভা", "প্রেজেন্টেশন"
    };

    private static final Pattern COURSE =
            Pattern.compile("\\b(CSE|ECE|MATH|HUM)\\s?-?\\s?\\d{4}\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CT =
            Pattern.compile("\\bct\\s?-?\\d?\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASS_TIME = Pattern.compile(
            "(class|lecture).*(\\b\\d{1,2}(:\\d{2})?\\s?(am|pm)\\b|tomorrow|today|sunday|monday|tuesday|wednesday|thursday|next week)"
                    + "|(tomorrow|today|sunday|monday|tuesday|wednesday|thursday).*(class|lecture)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static boolean looksRelevant(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        for (String w : WORDS) {
            if (t.contains(w)) return true;
        }
        return COURSE.matcher(text).find()
                || CT.matcher(text).find()
                || CLASS_TIME.matcher(text).find();
    }
}
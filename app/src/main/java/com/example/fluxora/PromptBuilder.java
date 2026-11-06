package com.example.fluxora;

import android.content.Context;
import com.example.fluxora.db.RoutineEvent;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PromptBuilder {

    private static final String[] SHORT_DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    public static String update(Context ctx, String message, List<RoutineEvent> events, boolean fromGroup) {
        LocalDate today = LocalDate.now();
        String todayStr = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US) + " " + today;

        Map<String, List<String>> slots = new LinkedHashMap<>();
        Map<String, String> teachers = new HashMap<>();
        for (RoutineEvent e : events) {
            if (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) {
                slots.computeIfAbsent(e.title, k -> new ArrayList<>())
                        .add(SHORT_DAYS[e.dayOfWeek - 1] + " " + e.startTime);
                if (e.details != null && !e.details.isEmpty() && !teachers.containsKey(e.title)) {
                    teachers.put(e.title, e.details);
                }
            }
        }
        StringBuilder courses = new StringBuilder();
        for (Map.Entry<String, List<String>> en : slots.entrySet()) {
            courses.append("- ").append(en.getKey())
                    .append(" (").append(String.join(", ", en.getValue())).append(")");
            String t = teachers.get(en.getKey());
            if (t != null) courses.append(" [").append(t).append("]");
            courses.append("\n");
        }
        if (courses.length() == 0) courses.append("(no classes saved yet)\n");

        String roll = UserProfile.roll(ctx);
        String serial = "";
        if (roll.length() >= 3) {
            try {
                serial = String.valueOf(Integer.parseInt(roll.substring(roll.length() - 3)));
            } catch (NumberFormatException ignore) { }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("You turn a student's message into items for their class routine. ")
                .append("The message is an announcement from a class WhatsApp group, or text the student typed or pasted.\n");
        sb.append("Today is ").append(todayStr)
                .append(". The week starts on Sunday and classes run Sunday to Thursday. ")
                .append("Class times are between 08:00 and 17:00, so times like 2:00, 3:20 or 4:10 without AM/PM are afternoon ")
                .append("(14:00, 15:20, 16:10) and 8:50 is morning.\n");
        sb.append("The student is in Section ").append(UserProfile.section(ctx))
                .append(", lab group ").append(UserProfile.group(ctx))
                .append(", roll ").append(roll);
        if (!serial.isEmpty()) sb.append(" (class serial ").append(serial).append(", the last three digits)");
        sb.append(".\n");
        sb.append("Use only the parts meant for this student:\n")
                .append("- Skip parts for the other section or the other lab groups (\"For section B\", \"B1: ...\").\n")
                .append("- If a line has a roll range like \"D-401 (1-43)\", match the class serial to the range to find this student's room.\n")
                .append("- A message that names no section applies to everyone.\n");
        if (fromGroup) {
            sb.append("Ignore chit-chat, questions, and anything that is not an announcement about a class, assignment, deadline, test or exam. ")
                    .append("If there is nothing to add, return [].\n");
        }
        sb.append("Students use short names. Match by abbreviation of the course title (ToC = Theory of Computation), ")
                .append("by the course prefix when it fits one theory course (ECE, HUM, MATH), by number (\"Lab 2200\" = CSE 2200), ")
                .append("or by the teacher's initials or name (shown in brackets). Prefer the theory course unless the message says lab.\n");
        sb.append("The student's regular classes (use these exact titles when the message refers to one):\n")
                .append(courses)
                .append("Message: \"").append(message.replace("\"", "'")).append("\"\n")
                .append("Return ONLY a JSON array (an empty array if there is nothing to add). Each item:\n")
                .append("{\"type\": one of EXTRA_CLASS, CANCELLED, ASSIGNMENT, DEADLINE, OTHER, ")
                .append("\"title\": the exact course title from the list for CANCELLED and EXTRA_CLASS, otherwise a short title, ")
                .append("\"date\": \"yyyy-MM-dd\" (work out words like tomorrow, Sunday or next Thursday from today's date), ")
                .append("\"startTime\": \"HH:mm\" in 24-hour time, or \"\" if not mentioned, ")
                .append("\"endTime\": \"HH:mm\" or \"\", \"room\": the room for this student or \"\", ")
                .append("\"details\": short extra info such as syllabus, topics, marks or what to submit}\n")
                .append("Rules:\n")
                .append("- CANCELLED: a regular class that will not happen. startTime is its usual time from the list, date is the day it was cancelled.\n")
                .append("- EXTRA_CLASS: an extra, make-up or rescheduled class on the new date and time, with the room if given.\n")
                .append("- If a class is moved and the message says which day it moves FROM, return CANCELLED for that old date and EXTRA_CLASS for the new one. ")
                .append("If the old day is not stated, return only the EXTRA_CLASS.\n")
                .append("- CT, class test, quiz, exam, viva: type OTHER, title like \"CT - <course title>\" or \"Lab Quiz - <course title>\", ")
                .append("details with the syllabus or topics and this student's room if one is given.\n")
                .append("- ASSIGNMENT: date is the due date. DEADLINE: any other deadline.\n")
                .append("- A room-only change for this student: type OTHER, title \"Room change - <course title>\", ")
                .append("date of that course's next class from the list, details with the new room.\n")
                .append("- Every item needs a date. Do not invent details that were not in the message.");
        return sb.toString();
    }
}
package com.example.fluxora.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routine_events")
public class RoutineEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String type;        // CLASS, EXTRA_CLASS, CANCELLED, ASSIGNMENT, DEADLINE, OTHER
    public int dayOfWeek;      // 1=Sun ... 7=Sat, used for weekly classes (0 if one-off)
    public String date;        // "yyyy-MM-dd" for one-off items (null for weekly)
    public String startTime;   // "HH:mm"
    public String endTime;     // "HH:mm"
    public String room;
    public String details;
    public String source;      // MANUAL, IMAGE, WHATSAPP, CHAT
    public long createdAt;
}
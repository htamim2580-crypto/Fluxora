package com.example.fluxora.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "attendance",
        indices = {@Index(value = {"courseTitle", "date", "startTime"}, unique = true)})
public class Attendance {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String courseTitle;
    public String date;        // yyyy-MM-dd
    public String startTime;   // HH:mm, separates two classes of one course on the same day
    public boolean present;
}
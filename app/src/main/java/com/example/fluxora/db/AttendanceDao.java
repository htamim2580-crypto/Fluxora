package com.example.fluxora.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AttendanceDao {
    @Query("DELETE FROM attendance")
    void deleteAll();
    @Query("SELECT * FROM attendance")
    List<Attendance> getAllNow();
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Attendance a);

    @Query("DELETE FROM attendance WHERE courseTitle = :title AND date = :date AND startTime = :startTime")
    void remove(String title, String date, String startTime);

    @Query("SELECT * FROM attendance")
    LiveData<List<Attendance>> getAll();
}
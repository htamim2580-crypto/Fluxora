package com.example.fluxora.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RoutineDao {
    @Insert
    long insert(RoutineEvent event);

    @Update
    void update(RoutineEvent event);

    @Delete
    void delete(RoutineEvent event);

    @Query("SELECT * FROM routine_events ORDER BY dayOfWeek, startTime")
    LiveData<List<RoutineEvent>> getAll();

    @Query("SELECT * FROM routine_events WHERE dayOfWeek = :day ORDER BY startTime")
    LiveData<List<RoutineEvent>> getByDay(int day);
}
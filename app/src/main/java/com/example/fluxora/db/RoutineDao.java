package com.example.fluxora.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RoutineDao {

    @Query("DELETE FROM routine_events")
    void deleteAll();
    @Query("SELECT COUNT(*) FROM routine_events WHERE type = :type AND title = :title AND date = :date AND IFNULL(startTime, '') = :startTime")
    int countSame(String type, String title, String date, String startTime);
    @Query("SELECT COUNT(*) FROM routine_events WHERE type = 'CANCELLED' AND title = :title AND date = :date")
    int countCancelled(String title, String date);
    @Query("SELECT * FROM routine_events")
    List<RoutineEvent> getAllNow();

    @Query("SELECT * FROM routine_events WHERE id = :id")
    RoutineEvent getById(long id);
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
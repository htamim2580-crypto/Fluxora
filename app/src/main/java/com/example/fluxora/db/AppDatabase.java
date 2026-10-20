package com.example.fluxora.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {RoutineEvent.class, Payment.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract RoutineDao routineDao();
    public abstract PaymentDao paymentDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService dbExecutor = Executors.newFixedThreadPool(2);

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "fluxora.db").build();
                }
            }
        }
        return INSTANCE;
    }
}
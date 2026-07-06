package com.fongmi.android.tv.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.fongmi.android.tv.bean.Download;

import java.util.List;

@Dao
public interface DownloadDao {

    @Query("SELECT * FROM Download ORDER BY createTime DESC")
    List<Download> getAll();

    @Query("SELECT * FROM Download ORDER BY createTime DESC")
    LiveData<List<Download>> getAllLive();

    @Query("SELECT * FROM Download WHERE id = :id")
    Download find(String id);

    @Query("SELECT * FROM Download WHERE id = :id")
    LiveData<Download> findLive(String id);

    @Query("SELECT * FROM Download WHERE vodName = :name LIMIT 1")
    Download findByVodName(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Download item);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Download item);

    @Delete
    void delete(Download item);

    @Query("DELETE FROM Download")
    void clear();

    default void insertOrUpdate(Download item) {
        if (find(item.getId()) != null) {
            update(item);
        } else {
            insert(item);
        }
    }
}

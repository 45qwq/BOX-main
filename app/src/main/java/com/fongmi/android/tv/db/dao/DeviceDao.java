package com.fongmi.android.tv.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.Device;

import java.util.List;

@Dao
public abstract class DeviceDao extends BaseDao<Device> {

    @Query("SELECT * FROM Device")
    public abstract List<Device> findAll();

    @Query("SELECT * FROM Device")
    public abstract LiveData<List<Device>> findAllLive();

    @Query("DELETE FROM Device")
    public abstract void delete();
}

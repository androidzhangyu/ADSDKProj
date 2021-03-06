package com.vito.ad.managers;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vito.ad.base.jdktool.ConcurrentHashSet;
import com.vito.ad.base.task.ADTask;
import com.vito.ad.base.task.DownloadTask;
import com.vito.ad.configs.Constants;
import com.vito.ad.services.DownloadService;
import com.vito.utils.Log;
import com.vito.utils.MD5Util;
import com.vito.utils.SharedPreferencesUtil;
import com.vito.utils.gsonserializer.UriDeserializer;
import com.vito.utils.gsonserializer.UriSerializer;

import org.json.JSONObject;

import java.lang.reflect.Type;

public class DownloadTaskManager {
    private DownloadService.DownloadBinder binder = null;
    private boolean isNeedStartTask = false;
    private ConcurrentHashSet<DownloadTask> DownloadingTasks = new ConcurrentHashSet<>();

    private ServiceConnection downloadServerConnect = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (DownloadService.DownloadBinder) service;
            if (isNeedStartTask)
                binder.getService().startTask();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    private static DownloadTaskManager instance = null;
    public static DownloadTaskManager getInstance(){
         if (instance == null){
              synchronized (DownloadTaskManager.class){
                  if (instance == null)
                    instance = new DownloadTaskManager();
              }
         }
         return instance;
    }

    private DownloadTaskManager(){
        InitWithConfig();
        // 开启下载服务
        Intent mIntent = new Intent();
        mIntent.setClass(AdManager.mContext, DownloadService.class);
        AdManager.mContext.startService(mIntent);

        Intent mIntent1 = new Intent();
        mIntent1.setClass(AdManager.mContext, DownloadService.class);
        AdManager.mContext.bindService(mIntent1, downloadServerConnect, Service.BIND_AUTO_CREATE);

    }

    private void InitWithConfig() {
        // TODO  读取文件，初始化之前的数据
        String downloadtasks_json = SharedPreferencesUtil.getStringValue(AdManager.mContext, Constants.AD_CONFIG_FILE_NAME, "downloadingtasks");
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriDeserializer())
                .create();
        Type type = new TypeToken<ConcurrentHashSet<DownloadTask>>(){}.getType();
        ConcurrentHashSet<DownloadTask> tasks = gson.fromJson(downloadtasks_json, type);
        if (tasks!=null){
            DownloadingTasks = tasks;
        }
    }

    public DownloadTask getTaskByJson(JSONObject json){
        Gson gson = new Gson();
        DownloadTask task = gson.fromJson(json.toString(), DownloadTask.class);
        Log.e("ADTEST", " parser json to downTask = "+gson.toString());
        return task;
    }

    public DownloadTask getTaskByJson(String jsonString){
        Gson gson = new Gson();
        DownloadTask task = gson.fromJson(jsonString, DownloadTask.class);
        Log.e("ADTEST", " parser json to downTask = "+gson.toString());
        return task;
    }

    public boolean pushTask( DownloadTask task){
        if (task == null)
            return false;
        synchronized (this) {
            DownloadingTasks.add(task); // 添加任务
            upDateSaveFile();
        }
        if (binder==null){
            Intent mIntent = new Intent();
            mIntent.setClass(AdManager.mContext, DownloadService.class);
            AdManager.mContext.bindService(mIntent, downloadServerConnect, Service.BIND_AUTO_CREATE);
            isNeedStartTask = true;
            return false;
        }
        binder.getService().startTask();

        return true;
    }

    public void notifyUpDate(){
        upDateSaveFile();
    }

    // 更新保存的任务文件
    private void upDateSaveFile() {
        // 序列化
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriSerializer())
                .create();
        String json = gson.toJson(DownloadingTasks);
        SharedPreferencesUtil.putStringValue(AdManager.mContext, Constants.AD_CONFIG_FILE_NAME, "downloadingtasks",json );
    }

    public ConcurrentHashSet<DownloadTask> getReadOnlyDownloadingTasks() {
        return DownloadingTasks;
    }

    public void setDownloadingTasks(ConcurrentHashSet<DownloadTask> downloadingTasks) {
        DownloadingTasks = downloadingTasks;
    }

    public DownloadTask getDownloadTaskByDownloadId(long id){
        ConcurrentHashSet<DownloadTask> Tasks = DownloadingTasks;
        for (DownloadTask task : Tasks) {
            if (task.getDownloadId() == id){
                return task;
            }
        }
        return null;
    }

    // 修改 类型 之前是long 现在改为int 保持和ADId 类型一致
    public DownloadTask getDownloadTaskByADId(int id){
        ConcurrentHashSet<DownloadTask> Tasks = DownloadingTasks;
        for (DownloadTask task : Tasks) {
            if (task.getId() == id){
                return task;
            }
        }
        return null;
    }

    public Service getService() {
        if (binder!=null)
            return binder.getService();
        return null;
    }

    public void exit() {
        if (AdManager.mContext!=null&&downloadServerConnect!=null)
            AdManager.mContext.unbindService(downloadServerConnect);
    }

    /**
     *  创建下载任务
     * @param mADTask 原始的广告任务
     * @return 新创建的下载任务
     */

    public DownloadTask buildDownloadTaskByADTask(ADTask mADTask) {
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.setType(Constants.APK_DOWNLOAD);
        downloadTask.setId(AdTaskManager.getInstance().getNextADID());
        String packageName = "";
        String name = "";
        downloadTask.setUrl(mADTask.getDownloadApkUrl());
        if (DownloadTaskManager.getInstance().getDownloadTaskByADId(mADTask.getId())!=null) {
            packageName = DownloadTaskManager.getInstance().getDownloadTaskByADId(mADTask.getId()).getPackageName();
            name = DownloadTaskManager.getInstance().getDownloadTaskByADId(mADTask.getId()).getName();
            name = name.substring(0, name.lastIndexOf(".")+1)+"apk";
        }else {
            name = MD5Util.encrypt(downloadTask.getUrl())+".apk";
        }
        downloadTask.setPackageName(packageName);
        downloadTask.setOriginId(mADTask.getId());
        downloadTask.setName(name);
        return downloadTask;
    }

    /**
     * fix bug  2018年9月14日14:15:09 之前删除实际上对比的是downloadid  有问题
     * @param id
     */
    public void removeTaskByADId(int id) {
        for (DownloadTask task : DownloadingTasks) {
            if (task.getOriginId() == id){
                DownloadingTasks.remove(task);
                upDateSaveFile();
                return;
            }
        }
    }

    /**
     * 通过downloadId 删除
     * @param id downloadId
     */
    public void removeTaskByDownloadId(long id) {
        for (DownloadTask task : DownloadingTasks) {
            if (task.getDownloadId() == id){
                DownloadingTasks.remove(task);
                upDateSaveFile();
                return;
            }
        }
    }

    // 使用url下载 APK文件
    public DownloadTask getDownloadTaskByURL(String url, String packageName) {
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.setType(Constants.APK_DOWNLOAD_URL);
        downloadTask.setId(AdTaskManager.getInstance().getNextADID());
        downloadTask.setUrl(url);
        downloadTask.setPackageName(packageName);
        String name = url.substring(url.lastIndexOf("/") + 1);
        name = name.substring(0, name.lastIndexOf(".")+1)+"apk";
        downloadTask.setOriginId(Constants.NoOriginId);
        downloadTask.setName(name);
        return downloadTask;
    }

}

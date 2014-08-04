package com.ktind.cgm.bgscout;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.mongodb.*;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by klee24 on 8/3/14.
 */
public class RemoteMongoDevice extends AbstractCGMDevice {
    private static final String TAG = RemoteMongoDevice.class.getSimpleName();
    private String mongoURI = "";
    private String collectionName="";
    private DBCollection deviceData;
    MongoClientURI uri = new MongoClientURI(mongoURI);
    DB db;
    MongoClient mongoClient = null;
    long lastQueryDate;
    EGVRecord[] lastRecord=new EGVRecord[1];

    public RemoteMongoDevice(String n,int deviceID,Context appContext,Handler mH){
        super(n,deviceID,appContext,mH);
        // Quasi race condition - CGM takes a second or 2 to read and upload while the "virtual CGM" takes less time.
        // Give it some time to settle. If not it'll try again in 45 seconds.
        this.setPollInterval(304000);
        // stop infinite loops!
        virtual = true;
    }

    @Override
    public int getCGMBattery() {
        return 100;
    }

    @Override
    public void connect() throws DeviceNotConnected {
    }

    @Override
    protected DeviceDownloadObject doDownload() {
        DeviceDownloadObject ddo=new DeviceDownloadObject();
        ddo.setDevice(this);
        ddo.setStatus(DownloadStatus.APPLICATIONERROR);
        ArrayList<EGVRecord> egvRecords=new ArrayList<EGVRecord>();
        ddo.setEgvRecords(new EGVRecord[0]);
        try {
            mongoClient = new MongoClient(uri);
            db = mongoClient.getDB(uri.getDatabase());
            deviceData = db.getCollection(collectionName);
            //FIXME Limit this unless we want to kill the heap over time...
            BasicDBObject query=new BasicDBObject("date", new BasicDBObject("$gt",lastQueryDate));
            DBCursor cursor=deviceData.find(query);
            try {
                List<DBObject> dbObjects=cursor.toArray();
                Log.d(TAG,"Size of response from mongo query: "+dbObjects.size());
                for (DBObject dbObject:dbObjects){
                    long recQueryDate=Long.valueOf(dbObject.get("date").toString());
                    Date recDate=new Date(recQueryDate);
                    int bgValue=Integer.valueOf(dbObject.get("sgv").toString());
                    Trend trend=Trend.values()[Integer.valueOf(dbObject.get("trend").toString())];
                    EGVRecord record;
                    if (recQueryDate>lastQueryDate){
                        lastQueryDate=recQueryDate;
                        record=new EGVRecord(bgValue,recDate,trend,true);
                        lastRecord[0]=record;
                    }else{
                        record=new EGVRecord(bgValue,recDate,trend,false);
                    }
                    egvRecords.add(record);
                    ddo.setEgvRecords(egvRecords.toArray(new EGVRecord[egvRecords.size()]));
                }
                ddo.setStatus(DownloadStatus.SUCCESS);
                //FIXME there has to be a more efficient way
                if (lastRecord!=null && ddo.getEgvRecords().length==0){
                    ddo.setStatus(DownloadStatus.NORECORDS);
                    ddo.setEgvRecords(lastRecord);
                }

            } finally {
                cursor.close();
            }
            Log.d(TAG, "Performing download of data from mongo for " + getName());
            mongoClient.close();
        }catch(UnknownHostException e){
            Log.e(TAG,"Unable to connect to MongoDB URI",e);
            ddo.setStatus(DownloadStatus.DEVICENOTFOUND);
            ddo.setEgvRecords(new EGVRecord[0]);
        }
        // FIXME potential cause for a race condition
        // Should be resolved by device proxy
        lastDownloadObject=ddo;
        return ddo;
    }

    @Override
    public void disconnect() {

    }
}
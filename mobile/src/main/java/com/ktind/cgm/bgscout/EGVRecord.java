package com.ktind.cgm.bgscout;

//import android.util.Log;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.Date;

/**
 Copyright (c) 2014, Kevin Lee (klee24@gmail.com)
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification,
 are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice, this
 list of conditions and the following disclaimer in the documentation and/or
 other materials provided with the distribution.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
public class EGVRecord implements Parcelable {
    private static final String TAG = EGVRecord.class.getSimpleName();
    protected int egv;
    protected Long date;
    protected Trend trend=Trend.NONE;
    protected GlucoseUnit unit=GlucoseUnit.MGDL;

    EGVRecord(int egv,long date,Trend trend){
        super();
        this.setEgv(egv);
        this.setDate(date);
        this.setTrend(trend);
    }

    EGVRecord(EGVRecord record){
        this.egv=record.getEgv();
        this.date=record.date;
        this.trend=record.getTrend();
        this.unit=record.getUnit();
    }

    public EGVRecord(){
        super();
    }

    public GlucoseUnit getUnit() {
        return unit;
    }

    public void setUnit(GlucoseUnit unit) {
        this.unit = unit;
    }

    public int getEgv() {
        return egv;
    }

    public void setEgv(int egv) {
        this.egv = egv;
    }

    public Date getDate() {
        return new Date(date);
    }

    public void setDate(long date) {
        this.date = date;
    }

    public void setDate(Date date){
        setDate(date.getTime());
    }

    public Trend getTrend() {
        return trend;
    }

    public void setTrend(Trend trend) {
        this.trend = trend;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(egv);
        dest.writeLong(date);
        dest.writeInt(trend.getVal());
        dest.writeInt(unit.getValue());
    }

    public static final Parcelable.Creator<EGVRecord> CREATOR
            = new Parcelable.Creator<EGVRecord>() {
        public EGVRecord createFromParcel(Parcel in) {
            return new EGVRecord(in);
        }

        public EGVRecord[] newArray(int size) {
            return new EGVRecord[size];
        }
    };

    private EGVRecord(Parcel in) {
        egv=in.readInt();
        date=in.readLong();
        trend=Trend.values()[in.readInt()];
        unit=GlucoseUnit.values()[in.readInt()];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || ((Object) this).getClass() != o.getClass()) return false;

        EGVRecord record = (EGVRecord) o;

        if (egv != record.egv){
            Log.d(TAG, "EGV Record failed on comparison egv");
            return false;
        }
        if (date==record.date){
            Log.d(TAG, "EGV Record failed on comparison date");
            return false;
        }

            if (trend != record.trend){
            Log.d(TAG, "EGV Record failed on comparison trend");
            return false;
        }
        if (unit != record.unit){
            Log.d(TAG, "EGV Record failed on comparison unit");
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = egv;
        result = 31 * result + date.hashCode();
        result = 31 * result + trend.hashCode();
        result = 31 * result + unit.hashCode();
        return result;
    }
}

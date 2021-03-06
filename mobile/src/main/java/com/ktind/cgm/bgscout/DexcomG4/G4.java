package com.ktind.cgm.bgscout.DexcomG4;

import android.content.Context;
import android.util.Log;

import com.ktind.cgm.bgscout.BitTools;
import com.ktind.cgm.bgscout.CGMTransportAbstract;
import com.ktind.cgm.bgscout.Constants;
import com.ktind.cgm.bgscout.DeviceException;
import com.ktind.cgm.bgscout.DeviceIOException;
import com.ktind.cgm.bgscout.GlucoseUnit;
import com.ktind.cgm.bgscout.NoDeviceFoundException;
import com.ktind.cgm.bgscout.OperationNotSupportedException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


/**
 Copyright (c) 2014, Kevin Lee  (klee24@gmail.com)
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
public class G4 {
    protected String serialNum;
    protected String receiverID;
    protected int cgmBattery=-1;
    private static final String TAG = G4.class.getSimpleName();
    CGMTransportAbstract cgmTransport;
    protected GlucoseUnit unit;
    protected Context context;

    public G4(Context context){
        this.context=context;
        cgmTransport=new G4USBSerialTransport(context);
    }

    public int getDeviceBattery() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        if (cgmBattery==-1)
            cgmBattery=getBatteryLevel();
        return cgmBattery;
    }

    public void connect() throws NoDeviceFoundException, DeviceIOException, OperationNotSupportedException {
        cgmTransport.open();
//        setup();
    }

    public boolean isConnected(){
        return cgmTransport.isOpen();
    }

    public void setup() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        if (cgmTransport.isOpen()) {
            unit = getUnit();
//            serialNum = getRcvrSerial();
            cgmBattery=getBatteryLevel();
        }else{
            Log.e(TAG,"Unable to setup device that I am not connected to");
        }
    }

    public void syncTime(){
        if (!cgmTransport.isOpen())
            return;
        try {
            Date dispDate=getDisplayTime();
            Long jitter=dispDate.getTime()-new Date().getTime();
            if (Math.abs(jitter) > Constants.TIMEDRIFTOLERANCE ) {
                Log.w(TAG,"Device time off by "+jitter+" ms");
                this.syncTimeToDevice();
            }
        }catch (DeviceException e){
            Log.e(TAG,"Unable to syncTime to device");
        }
    }

    public void disconnect() {
        cgmTransport.close();
    }

    public void setChargeDevice(boolean c){
        cgmTransport.setChargeReceiver(c);
    }

    //    @Override
    public G4EGVRecord[] getReadings() {
        return this.getReadings(G4Constants.defaultReadings);
    }

    public G4EGVRecord[] getReadings(int numReadings) {
        return new G4EGVRecord[0];
    }

    // Retrieves the last 4 pages
    public ArrayList<? extends G4Record> getLastRecords() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        G4Partition partition=getDBPageRange(G4RecType.EGVDATA);
        return getRecords(partition.lastPage - 3, 4,G4RecType.EGVDATA);
//        ArrayList<G4Record> results=getRecords(partition.lastPage - 3, 4,G4RecType.EGVDATA);
//        return results;
    }

    // TODO Possibly abstract this out and have it return a collection of parsed records?
    public ArrayList<G4Record> getRecords(int firstPage,int lastPage, G4RecType recType) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        G4DBPage[] pages=getDBPages(recType,firstPage,lastPage);
        ArrayList<G4Record> records=new ArrayList<G4Record>();
        int totalNumRecords=0;
        for (G4DBPage page: pages) {
            records.addAll(parsePage(page));
            Log.v(TAG,"Start index: "+totalNumRecords+" Record count: "+page.PageHeader.NumberOfRecords+" End: "+(page.PageHeader.NumberOfRecords*totalNumRecords+page.PageHeader.NumberOfRecords));
            totalNumRecords+=page.PageHeader.NumberOfRecords;
        }
        Log.v(TAG,"Record type: "+pages[0].PageHeader.RecordType.toString()+" PageCount: "+pages.length+" Total records: "+totalNumRecords);
        Log.d(TAG, "In getRecords: "+records.size());
        return records;
    }


    public boolean ping() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        if (!cgmTransport.isOpen()){
            Log.e(TAG,"Ping failed - not connected to device");
            return false;
        }
        writeCmd(G4RcvrCmd.PING);
        byte[] result=readResponse();
        if (result==null || result.length!=6){
            Log.e(TAG,"Ping unsuccessful");
            return false;
        }
        Log.i(TAG,"Ping successful");
        return true;
    }

    public G4Partition getDBPageRange(G4RecType recordType) throws DeviceIOException, NoDeviceFoundException, OperationNotSupportedException {
        G4Partition response=new G4Partition();
        writeCmd(G4RcvrCmd.READDATABASEPAGERANGE,recordType.getValue());
        byte[] responseBuff = readResponse();
        if (responseBuff==null)
            throw new DeviceIOException("Null response while trying to read record type: "+recordType.toString());
        if ( responseBuff.length!=8)
            throw new DeviceIOException("Problem reading response while trying to read record type "+recordType.toString()+" response size="+responseBuff.length+" response: "+String.valueOf(responseBuff));
        byte[] firstPage = {responseBuff[0],responseBuff[1],responseBuff[2],responseBuff[3]};
        byte[] lastPage = {responseBuff[4],responseBuff[5],responseBuff[6],responseBuff[7]};
        response.Partition=recordType;
        response.firstPage= BitTools.byteArraytoInt(firstPage);
        response.lastPage= BitTools.byteArraytoInt(lastPage);
        Log.d(TAG,"Partition: "+response.Partition.toString()+"First Page: "+response.firstPage+"Last Page: "+response.lastPage);
        return response;
    }

    // There has to be a better way to do this?
    private void writeCmd(G4RcvrCmd cmd, byte value) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        byte[] b={value};
        writeCmd(cmd,b);
    }

    public GlucoseUnit getUnit() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        if (unit!=GlucoseUnit.NONE){
            return unit;
        }
        writeCmd(G4RcvrCmd.READGLUCOSEUNIT);
        byte[] res=readResponse();
        int result=0;
        if (res!=null && res.length==0)
            throw new DeviceIOException("Null or 0 byte response from device while trying to read units");
        if (res!=null && res.length>0)
            result=(int) res[0];
        if (res!=null && res.length > GlucoseUnit.values().length)
            throw new DeviceIOException("Unexpected response from device while trying to determine units. Response was: "+result);
        setUnit(GlucoseUnit.values()[result]);
        return unit;
    }

    public void setUnit(GlucoseUnit u){
        unit=u;
    }

    public String getTransmitterId() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        writeCmd(G4RcvrCmd.READTRANSMITTERID);
        String result=null;
        try {
            result = new String(readResponse(), "UTF-8");
        }catch(UnsupportedEncodingException e){
            Log.e(TAG,"Exception converting byte array to String",e);
        }
        return result;
    }

    public String getBatteryState() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        writeCmd(G4RcvrCmd.READBATTERYSTATE);
        String result="Unable to determine battery state";
        byte[] response=readResponse();

        if (response!=null && response.length>=0)
            result= G4BatteryState.values()[response[0]-1].toString();
        return result;
    }

    public int getBatteryLevel() throws NoDeviceFoundException, DeviceIOException, OperationNotSupportedException {
        writeCmd(G4RcvrCmd.READBATTERYLEVEL);
        int result=0;
        result = BitTools.byteArraytoInt(readResponse());
        return result;
    }

    public Date getDisplayTime() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        return new Date(getDisplayTimeLong());
    }

    public void syncTimeToDevice() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        Calendar mCalendar = new GregorianCalendar();
        TimeZone mTimeZone = mCalendar.getTimeZone();
        long dispTimeOffset=new Date().getTime()/1000-G4Constants.RECEIVERBASEDATE/1000-getSystemTimeLong();
        if (dispTimeOffset > G4Constants.RECEIVERBASEDATE)
            throw new OperationNotSupportedException("Uploader time is pre-Jan 1 2009");
        if (mTimeZone.inDaylightTime(new Date())){
            dispTimeOffset+=3600L; // 1 hour for daylight time if it is observed
        }
        byte[] byteArray=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) dispTimeOffset).array();
        writeCmd(G4RcvrCmd.WRITEDISPLAYTIMEOFFSET, byteArray);
        byte[] resp=readResponse();
        Log.i(TAG,"Sync'd device time with cell. Set display time offset to: "+dispTimeOffset);
    }

    public long getDisplayTimeLong() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
//        Calendar mCalendar = new GregorianCalendar();
//        TimeZone mTimeZone = mCalendar.getTimeZone();
        long dispTime=G4Constants.RECEIVERBASEDATE+getDisplayTimeOffsetLong()*1000L+getSystemTimeLong()*1000L;
//        if (mTimeZone.inDaylightTime(new Date()))
//            dispTime-=3600000L;
        Log.v(TAG,"getDisplayTimeLong: "+dispTime);
        return dispTime;
    }

    public long getSystemTimeLong() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        writeCmd(G4RcvrCmd.READSYSTEMTIME);
        long result=0;
        result = BitTools.byteArraytoInt(readResponse());
        Log.v(TAG,"getSystemTimeLong=>"+result);
        return result;
    }

    protected long getDisplayTimeOffsetLong() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        writeCmd(G4RcvrCmd.READDISPLAYTIMEOFFSET);
        long result=0;
        result = BitTools.byteArraytoInt(readResponse());
        Log.v(TAG,"getDisplayTimeOffsetLong=>"+result);
        return result;
    }

    protected long getSystemTimeOffsetLong() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        writeCmd(G4RcvrCmd.READSYSTEMTIMEOFFSET);
        long result=0;
        result = BitTools.byteArraytoInt(readResponse());
        Log.v(TAG,"getSystemTimeOffsetLong=>"+result);
        return result;
    }

    protected String getDatabasePartitionInfo() throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        writeCmd(G4RcvrCmd.READDATABASEPARTITIONINFO);
        return new String(readResponse());
    }

    //CRC methods
    public static int calcCrc16 (byte [] buff) {
        int crc = 0;
        for (int i = 0; i < buff.length; i++)
        {
            crc = ((crc  >>> 8) | (crc  << 8) )& 0xffff;
            crc ^= (buff[i] & 0xff);
            crc ^= ((crc & 0xff) >> 4);
            crc ^= (crc << 12) & 0xffff;
            crc ^= ((crc & 0xFF) << 5) & 0xffff;
        }
        crc &= 0xffff;
        return crc;
    }

    public int writeCmd(G4RcvrCmd rcvrCmd, byte [] payload) throws NoDeviceFoundException, OperationNotSupportedException, DeviceIOException {
        Log.v(TAG,"Attempting to write to receiver");
        Log.i(TAG,"Executing command "+rcvrCmd.toString());
        if(!cgmTransport.isOpen()){
            Log.e(TAG,"Write failed - not connected to device");
            throw new NoDeviceFoundException("Not connected to device");
        }
        int bytesWritten=0;

        // Retrieve how many bytes should be sent by this command
        int bytesToWrite=rcvrCmd.getCmdSize();
        Log.v(TAG, "Bytes to write: " + bytesToWrite);
        int calcBytesToWrite=6;
        if (payload!=null)
            calcBytesToWrite = 4 + payload.length + 2;
        if (bytesToWrite != calcBytesToWrite){
            Log.e(TAG,"Insufficient data for command");
            return 0;
        }
        if (bytesToWrite==-1){
            throw new OperationNotSupportedException("Command "+rcvrCmd.toString()+" has not been implemented");
        }
        byte[] packet = new byte[bytesToWrite];

        packet[0]=0x01; // Always 1

        // next two bytes are the size of the command => SOF+packet size+command+payload+crc
        packet[1]=(byte) (bytesToWrite & 0xFF);
        packet[2]=(byte) (bytesToWrite >> 8 & 0xFF);
        packet[3]=rcvrCmd.getValue();

        // Copy the payload if it exists
        if (payload!=null && bytesToWrite>6 && payload.length>0)
            System.arraycopy(payload,0,packet,4,bytesToWrite-6);
        byte [] crcPacket=new byte[bytesToWrite-2];
        System.arraycopy(packet,0,crcPacket,0,bytesToWrite-2);
        int crc=calcCrc16(crcPacket);
        packet[bytesToWrite-2]=(byte)(crc & 255);
        packet[bytesToWrite-1]=(byte)(crc >> 8 & 255);

        try {
            Log.v(TAG,"Writing("+rcvrCmd.toString()+"): "+ BitTools.bytesToHex(packet));
            bytesWritten=cgmTransport.write(packet,G4Constants.defaultWriteTimeout);
            Log.v(TAG,"Bytes written - "+bytesWritten);
        } catch (IOException e) {
            throw new DeviceIOException("Unable to write to Dexcom G4");
//            Log.e(TAG, "Unable to write to Dexcom G4", e);

        }
        return bytesWritten;
    }


    public int writeCmd(G4RcvrCmd rcvCmd) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        return writeCmd(rcvCmd,null);
    }

    public byte[] readResponse() throws DeviceIOException, NoDeviceFoundException {
        return this.readResponse(G4Constants.defaultReadTimeout);
    }

    public byte[] readResponse(int millis) throws NoDeviceFoundException, DeviceIOException {
        Log.v(TAG,"Attempting to read to receiver");
        if(!cgmTransport.isOpen()){
            Log.e(TAG,"Read failed - not connected to device");
            throw new NoDeviceFoundException("Read failed - not connected to device");
        }

        int bytesRead=0;
        // Seems we can't read fewer bytes than they send..
        // but we can request more bytes than they'll send.
        // Setting max response buffer to 3072 to prevent
        // a read failure.
        // Max size required should be somewhere around 2122
        // while reading database pages. Let's just round
        // that up a bit
        byte [] responseBuffer = new byte[3072];

        try {
            bytesRead=cgmTransport.read(responseBuffer,millis);
            Log.v(TAG,"Bytes read - "+bytesRead);
        } catch (IOException e) {
            Log.e(TAG,"Unable to read headers from Dexcom G4");
            throw new DeviceIOException("Unable to read headers from Dexcom G4");
        }
        if (responseBuffer[0]!=0x01) {
            Log.e(TAG, "Unexpected response back while parsing header: "+ BitTools.bytesToHex(responseBuffer));
            throw new DeviceIOException("Unexpected response back while parsing header: "+ BitTools.bytesToHex(responseBuffer));
        }
        int bytesToRead=(int)responseBuffer[2]<<8 ^ (int)responseBuffer[1];
        Log.v(TAG,"Calculated bytes to read at "+bytesRead);
        if (bytesToRead!=bytesRead) {
            Log.e(TAG, "Calculated bytes to read does not equal the bytes actually read");
            throw new DeviceIOException("Calculated bytes to read does not equal the bytes actually read");
        }

        byte [] header = new byte[4];
        byte [] body = new byte[bytesRead-6];
        byte [] crc=new byte[2];

        System.arraycopy(responseBuffer,0,header,0,4);
        System.arraycopy(responseBuffer,4+(bytesToRead-6),crc,0,2);
        System.arraycopy(responseBuffer,4,body,0,bytesRead-6);

        Log.v(TAG,"Header hex:"+ BitTools.bytesToHex(header));
        Log.v(TAG,"Body hex:"+ BitTools.bytesToHex(body));
        Log.v(TAG,"CRC hex: "+ BitTools.bytesToHex(crc));
        byte[] crcCheckArray=new byte[bytesToRead-2];
        System.arraycopy(responseBuffer,0,crcCheckArray,0,bytesToRead-2);
        int calcCRC=calcCrc16(crcCheckArray);
        int crcInt=(crc[0] & 0xFF);
        crcInt+=(crc[1] << 8) & 0xFF00;
        if (calcCRC!=crcInt) {
            Log.d(TAG, "Calculated CRC: " + calcCRC+"Response CRC: " + crcInt);
            Log.e(TAG, "CRC check failed!");
            throw new DeviceIOException("Calculated CRC: " + calcCRC+"Response CRC: " + crcInt+". CRC check failed!");
        }else{
            Log.v(TAG,"Successful CRC check");
        }
        return body;
    }

    private G4DBPageHeader getPageHeader(G4RecType recType,int pageNumber) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        G4DBPageHeader result=new G4DBPageHeader();
        byte[] requestPayload=new byte[5];
        requestPayload[0]=recType.getValue();
        System.arraycopy(BitTools.intToByteArray(pageNumber),0,requestPayload,1,4);
        writeCmd(G4RcvrCmd.READDATABASEPAGEHEADER, requestPayload);
        byte[] resultBuffer=readResponse();
        byte[] FirstRecordIndexArray=new byte[4];
        byte[] NumberOfRecordsArray=new byte[4];
//        byte[] RecordTypeArray=new byte[1];
//        byte[] RevisionArray=new byte[1];
        byte[] PageNumberArray=new byte[4];
        byte[] Reserved2Array=new byte[4];
        byte[] Reserved3Array=new byte[4];
        byte[] Reserved4Array=new byte[4];
        byte[] CRCArray=new byte[2];

        if (resultBuffer.length > 26) {
            System.arraycopy(resultBuffer, 0, FirstRecordIndexArray, 0, 4);
            System.arraycopy(resultBuffer, 4, NumberOfRecordsArray, 0, 4);
//        System.arraycopy(resultBuffer,8,RecordTypeArray,0,1);
//        System.arraycopy(resultBuffer,9,RevisionArray,0,1);
            System.arraycopy(resultBuffer, 10, PageNumberArray, 0, 4);
            System.arraycopy(resultBuffer, 14, Reserved2Array, 0, 4);
            System.arraycopy(resultBuffer, 18, Reserved3Array, 0, 4);
            System.arraycopy(resultBuffer, 22, Reserved4Array, 0, 4);
            System.arraycopy(resultBuffer, 26, CRCArray, 0, 2);
            result.FirstRecordIndex = BitTools.byteArraytoInt(FirstRecordIndexArray);
            result.NumberOfRecords = BitTools.byteArraytoInt(NumberOfRecordsArray);
            result.RecordType = G4RecType.values()[(int) resultBuffer[8]];
            result.Revision = resultBuffer[9];
            result.PageNumber = BitTools.byteArraytoInt(PageNumberArray);
            result.Reserved2 = BitTools.byteArraytoInt(Reserved2Array);
            result.Reserved3 = BitTools.byteArraytoInt(Reserved3Array);
            result.Reserved4 = BitTools.byteArraytoInt(Reserved4Array);
            int crcInt = (CRCArray[0] & 0xFF);
            crcInt += (CRCArray[1] << 8) & 0xFF00;
            result.Crc = crcInt;
        } else {
            throw new DeviceIOException("Response size for getPageHeader("+recType.toString()+","+pageNumber+") smaller than expected");
        }
        Log.v(TAG,"FirstRecordIndex: "+result.FirstRecordIndex+" NumberOfRecords: "+result.NumberOfRecords+" RecordType: "+result.RecordType.toString()+" Revision: "+result.Revision+" PageNumber: "+result.PageNumber+"CRC: "+result.Crc);
        return result;
    }

    public G4DBPage[] getDBPages(G4RecType recType,int startPage, int numPages) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        Log.v(TAG,"Requesting "+numPages+" starting with "+startPage);
        byte [] requestPayload=new byte[6];
        requestPayload[0]=recType.getValue();
        requestPayload[1]=(byte) (startPage & 0xFF);
        requestPayload[2]=(byte) ((startPage >> 8) & 0xFF);
        requestPayload[3]=(byte) ((startPage >> 16) & 0xFF);
        requestPayload[4]=(byte) ((startPage >> 24) & 0xFF);
        requestPayload[5]=(byte) numPages;
        writeCmd(G4RcvrCmd.READDATABASEPAGES, requestPayload);
        byte[] response=readResponse();
        Log.v(TAG,"Response Length: "+response.length);
        G4DBPage[] pages=new G4DBPage[numPages];
        for (int i=0;i<numPages;i++){
            pages[i]=new G4DBPage();
            pages[i].PageHeader=getPageHeader(recType,startPage+i);
            int pgBoundary=528*(i+1);
            if (response.length > pgBoundary-500) {
                System.arraycopy(response, pgBoundary - 500, pages[i].PageData, 0, 500);
            } else {
                Log.wtf(TAG, "Response length is less than the calculated page boundary");
                Log.d(TAG,"response.length: "+response.length);
                Log.d(TAG,"pgBoundary: "+pgBoundary);
            }
            Log.v(TAG, "Page (" + i + "):" + BitTools.bytesToHex(pages[i].PageData));
            Log.v(TAG,"Page length ("+i+"):"+pages[i].PageData.length);
        }
        Log.v(TAG,"Returning "+pages.length+" pages from getDBPages");
        return pages;
    }

    public ArrayList<? extends G4Record> parsePage(G4DBPage page) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
        G4Record rec=null;
        if (page.PageHeader.RecordType==G4RecType.EGVDATA) {
            rec=new G4EGVRecord();
        }
        if (rec==null) {
            throw new UnsupportedOperationException("Have not implemented the code to retrieve and parse for record type " + page.PageHeader.RecordType);
        } else {
            ArrayList<? extends G4Record> results=rec.parse(page);
            Log.d(TAG,"In parsePage: "+results.size());
            return results;
        }

    }

//    public G4EGVRecord[] parseEGVPage(G4DBPage page) throws OperationNotSupportedException, NoDeviceFoundException, DeviceIOException {
//        G4EGVRecord[] results=new G4EGVRecord[page.PageHeader.NumberOfRecords];
//        Log.d(TAG,"Processing "+page.PageHeader.NumberOfRecords+" records from page #"+page.PageHeader.PageNumber);
//        for (int i=0;i<page.PageHeader.NumberOfRecords;i++){
//            if (page.PageData.length>i*13){
//                results[i]=new G4EGVRecord();
//                byte[] recordBuffer=new byte[13];
//                System.arraycopy(page.PageData, i * 13, recordBuffer, 0, 13);
//                byte[] sysTimeArray=new byte[4];
//                byte[] dispTimeArray=new byte[4];
//                byte[] egvwflagArray=new byte[2];
//                byte[] dirnoiseArray=new byte[1];
////                byte[] crcArray=new byte[2];
//                System.arraycopy(recordBuffer, 0, sysTimeArray, 0, 4);
//                System.arraycopy(recordBuffer, 4, dispTimeArray, 0, 4);
//                System.arraycopy(recordBuffer, 8, egvwflagArray, 0, 2);
//                System.arraycopy(recordBuffer, 10, dirnoiseArray, 0, 1);
////                System.arraycopy(recordBuffer, 11, crcArray, 0, 2);
//                results[i].setSystemTime(BitTools.byteArraytoInt(sysTimeArray));
//                long dtime=(long) BitTools.byteArraytoInt(dispTimeArray)*1000;
//                Calendar mCalendar = new GregorianCalendar();
//                TimeZone mTimeZone = mCalendar.getTimeZone();
//
//                long displayTimeLong=G4Constants.RECEIVERBASEDATE+dtime;
//                if (mTimeZone.inDaylightTime(new Date())){
//                    displayTimeLong-=3600000L;
//                }
//                Date displayTimeDate=new Date(displayTimeLong);
//
//                results[i].setDate(displayTimeDate);
//                int bgValue= BitTools.byteArraytoInt(egvwflagArray) & 0x3FF;
//                // This means we've found the end
//                if (bgValue==1023) {
//                    Log.d(TAG,"Last reading found in this page");
//                    break;
//                }
//                results[i].setEgv(bgValue);
//                results[i].setSpecialValue(null);
////                results[i].setUnit(getUnit());
////                results[i].s(deviceUnits);
//                for (G4EGVSpecialValue e: G4EGVSpecialValue.values()) {
//                    if (e.getValue()==bgValue) {
//                        results[i].setSpecialValue(G4EGVSpecialValue.getEGVSpecialValue(bgValue));
//                        Log.w(TAG,"Special value set: "+results[i].getSpecialValue().toString());
//                        break;
//                    }
//                }
//                int trendNoise= BitTools.byteArraytoInt(dirnoiseArray);
//                results[i].setTrend(Trend.values()[trendNoise & 0xF]);
//                results[i].setNoiseMode(G4NoiseMode.getNoiseMode((byte)(trendNoise & 0xF)>>4));
//                Log.v(TAG,"Reading time("+i+"): "+results[i].getDate().toString()+" EGV: "+results[i].getEgv()+" Trend: "+results[i].getTrend().toString()+" Noise: "+results[i].getNoiseMode().toString());
//            } else {
//                Log.w(TAG,"Record ("+i+") appears to be truncated in page number "+page.PageHeader.PageNumber);
//            }
//        }
////        Log.d(TAG,"Number of Records: "+results.length);
//        return results;
//    }

    public String getRcvrSerial() throws OperationNotSupportedException, DeviceIOException, NoDeviceFoundException {
        serialNum = getParam(G4RecType.MANUFACTURINGDATA, "SerialNumber");
        return serialNum;
    }

    private String getParam(G4RecType recType,String param) throws DeviceIOException, NoDeviceFoundException, OperationNotSupportedException {
        G4Partition part=getDBPageRange(recType);
        String result="";
//        Charset charset=
        G4DBPage[] pages=getDBPages(recType,part.firstPage,1);
        String data=new String();
        for (G4DBPage page:pages){
            int i;
            // Ugly code to capture the null terminated string
            for (i = 1; i < page.PageData.length && page.PageData[i] != 0x00; i++) { }
            if (i<8)
                throw new DeviceIOException("Expected larger page data in getParam for " + recType.toString() + " parameter: " + param);
            // Strip the header and reduce the size of the string by the header length
            data+=new String(page.PageData,8,i-8);
        }
        try {
            InputStream is = new ByteArrayInputStream(data.getBytes("UTF-8"));
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            DocumentBuilder builder=factory.newDocumentBuilder();
            Document dom=builder.parse(is);
            String elemName;
            if (recType==G4RecType.PCSOFTWAREPARAMETER) {
                elemName = "PCParameterRecord";
            }else if (recType==G4RecType.MANUFACTURINGDATA){
                elemName="ManufacturingParameters";
            } else {
                return "";
            }
            Element elem = (Element) dom.getElementsByTagName(elemName).item(0);
            result=elem.getAttribute(param);
        }catch (Exception e) {
            Log.e(TAG,"Problem parsing XML from "+recType.toString()+" partition in getParam",e);
        }
        Log.d(TAG,recType.toString()+" data:"+data+"Result: "+result);
        return result;
    }

    public String getRcvrID() throws OperationNotSupportedException, DeviceIOException, NoDeviceFoundException {
        // Only get this value once per instance of a G4Device
        if (receiverID.equals("")) {
            receiverID = getParam(G4RecType.PCSOFTWAREPARAMETER, "ReceiverId");
        } else {
            Log.d(TAG, "Returning cached results for ReceiverId");
        }
        return receiverID;
    }

}
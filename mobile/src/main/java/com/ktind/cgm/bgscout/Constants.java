package com.ktind.cgm.bgscout;

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
public class Constants {
    public final static int DEFAULTRETRYINTERVAL=45000;
    public final static int TIMEDRIFTOLERANCE=3000;
    public final static int READINGDELAY=TIMEDRIFTOLERANCE+5000;
    public final static String UIDO_QUERY="com.ktind.cgm.UIDO_QUERY";
    public final static String STOP_DOWNLOAD_SVC="com.ktind.cgm.STOPSERVICE";
    public final static String START_DOWNLOAD_SVC="com.ktind.cgm.STARTSERVICE";
    public final static String SNOOZE_INTENT="com.ktind.cgm.SNOOZE_ALARM";
    public final static String DEVICE_POLL="com.ktind.cgm.DEVICE_POLL";
    public final static String UI_UPDATE="com.ktind.cgm.UI_READING_UPDATE";
    // UPDATE the DATABASE VERSION and FIX the update routine if this value is modified (increased)!
    public final static String[] DEVICES={"device_1","device_2","device_3","device_4"};
    public final static int CONTACTREQUESTCODE=600;
    public final static String CONTACTDATAURISUFFIX="_contact_data_uri";
}

package com.aoindustries.aoserv.jilter;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.jilter.config.EmailLimit;

/**
 * Keeps track of the number of emails for one package and direction (in, out, relay).
 *
 * @author  AO Industries, Inc.
 */
public class EmailCounter {

    private final String packageName;
    private final EmailLimit emailLimit;

    private int emailCount;
    private long lastDecrementTime;
    private int notifyDelayMinutes;
    private long lastNotifyTime;

    public EmailCounter(String packageName, EmailLimit emailLimit) {
        this.packageName = packageName;
        this.emailLimit = emailLimit;
        reset(System.currentTimeMillis());
    }

    public String getPackageName() {
        return packageName;
    }
    
    public EmailLimit getEmailLimit() {
        return emailLimit;
    }
    
    public int getEmailCount() {
        return emailCount;
    }
    
    /**
     * All access to this method should be synchronized externally.
     */
    public long getLastDecrementTime() {
        return lastDecrementTime;
    }
    
    /**
     * All access to this method should be synchronized externally.
     */
    public void reset(long currentTimeMillis) {
        emailCount = 0;
        lastDecrementTime = currentTimeMillis;
        notifyDelayMinutes = 0;
        lastNotifyTime = -1;
    }
    
    /**
     * All access to this method should be synchronized externally.
     */
    public void decrement(long decrementCount, long currentTimeMillis) {
        if(decrementCount > emailCount) {
            emailCount = 0;
            lastDecrementTime = currentTimeMillis;
            //reset(currentTimeMillis);
        } else {
            emailCount-=decrementCount;
            // Is this timed right?
            lastDecrementTime = lastDecrementTime + (long)((decrementCount * 1000L) / emailLimit.getRate());
        }
    }

    /**
     * All access to this method should be synchronized externally.
     */
    public void increment() {
        emailCount++;
    }

    public int getNotifyDelayMinutes() {
        return notifyDelayMinutes;
    }

    public void setNotifyDelayMinutes(int notifyDelayMinutes) {
        this.notifyDelayMinutes = notifyDelayMinutes;
    }

    /**
     * Gets the last notify time or <code>-1</code> if not yet notified.
     */
    public long getLastNotifyTime() {
        return lastNotifyTime;
    }

    public void setLastNotifyTime(long lastNotifyTime) {
        this.lastNotifyTime = lastNotifyTime;
    }
}

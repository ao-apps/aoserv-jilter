/*
 * aoserv-jilter - Mail filter for the AOServ Platform.
 * Copyright (C) 2007-2013, 2020, 2021, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of aoserv-jilter.
 *
 * aoserv-jilter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aoserv-jilter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with aoserv-jilter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.aoserv.jilter;

import com.aoindustries.aoserv.jilter.config.EmailLimit;

/**
 * Keeps track of the number of emails for one business and direction (in, out, relay).
 *
 * @author  AO Industries, Inc.
 */
public final class EmailCounter {

  private final String accounting;
  private final EmailLimit emailLimit;

  private int emailCount;
  private long lastDecrementTime;
  private int notifyDelayMinutes;
  private long lastNotifyTime;

  public EmailCounter(String accounting, EmailLimit emailLimit) {
    this.accounting = accounting;
    this.emailLimit = emailLimit;
    reset(System.currentTimeMillis());
  }

  public String getAccounting() {
    return accounting;
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
    if (decrementCount > emailCount) {
      emailCount = 0;
      lastDecrementTime = currentTimeMillis;
      //reset(currentTimeMillis);
    } else {
      emailCount -= decrementCount;
      // Is this timed right?
      lastDecrementTime += (long) ((decrementCount * 1000L) / emailLimit.getRate());
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

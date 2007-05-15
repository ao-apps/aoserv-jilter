package com.aoindustries.aoserv.jilter;

/*
 * Copyright 2007 by AO Industries, Inc.,
 * 816 Azalea Rd, Mobile, Alabama, 36693, U.S.A.
 * All rights reserved.
 */

/**
 * Wraps the details that will be sent via a notice.
 *
 * @author  AO Industries, Inc.
 */
public class Notice {

    final private long noticeTimeMillis;
    final private String smtpServer;
    final private String from;
    final private String to;
    final private String subject;
    final private String message;
    
    public Notice(long noticeTimeMillis, String smtpServer, String from, String to, String subject, String message) {
        this.noticeTimeMillis = noticeTimeMillis;
        this.smtpServer = smtpServer;
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.message = message;
    }

    public long getNoticeTimeMillis() {
        return noticeTimeMillis;
    }

    public String getSmtpServer() {
        return smtpServer;
    }
    
    public String getFrom() {
        return from;
    }
    
    public String getTo() {
        return to;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public String getMessage() {
        return message;
    }
}

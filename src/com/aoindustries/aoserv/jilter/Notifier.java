package com.aoindustries.aoserv.jilter;

/*
 * Copyright 2007-2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import java.util.Date;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Notifies administrators in a background thread.
 *
 * @author  AO Industries, Inc.
 */
public class Notifier implements Runnable {

    private static final Log log = LogFactory.getLog(Notifier.class);

    private Notifier() {
    }
    
    final private static Queue<Notice> noticeQueue = new LinkedList<Notice>();

    static private Thread queueThread;

    public static void enqueueNotice(Notice notice) {
        // Add to queue
        synchronized(noticeQueue) {
            noticeQueue.add(notice);
            if(queueThread!=null) {
                // Notify thread if already created
                noticeQueue.notify();
            } else {
                // Create thread if not yet running
                (queueThread = new Thread(new Notifier())).start();
            }
        }
    }

    public void run() {
        while(true) {
            try {
                while(true) {
                    Notice notice;
                    synchronized(noticeQueue) {
                        while(noticeQueue.isEmpty()) noticeQueue.wait(5*60*1000);
                        notice = noticeQueue.remove();
                    }
                    sendNotice(notice);
                }
            } catch(ThreadDeath TD) {
                throw TD;
            } catch(Throwable T) {
                if(log.isErrorEnabled()) log.error(null, T);
            }
            try {
                Thread.sleep(10*1000);
            } catch(InterruptedException err) {
                if(log.isWarnEnabled()) log.warn(null, err);
            }
        }
    }

    /**
     * Send email for notification
     */
    private static void sendNotice(Notice notice) {
        String smtpServer = notice.getSmtpServer();
        // Don't send email when null or empty
        if(smtpServer!=null && (smtpServer=smtpServer.trim()).length()>0) {
            // Try to send to each recipient separately
            String[] tos = notice.getTo().split(",");
            for(int c=0;c<tos.length;c++) {
                String to = tos[c].trim();
                if(to.length()>0) {
                    try {
                        if(log.isDebugEnabled()) {
                            log.debug("smtpServer="+smtpServer);
                            log.debug("to="+notice.getTo());
                            log.debug("from="+notice.getFrom());
                            log.debug("subject="+notice.getSubject());
                        }
                        Properties props=new Properties();
                        props.put("mail.smtp.host", smtpServer);
                        Session session=Session.getDefaultInstance(props, null);
                        if(log.isDebugEnabled()) log.debug("Got Session");
                        Message msg=new MimeMessage(session);
                        msg.setSubject(notice.getSubject());
                        msg.setFrom(new InternetAddress(notice.getFrom()));
                        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                        // Set a high priority
                        msg.setHeader("X-Priority", "1");
                        //msg.setHeader("Priority", "Urgent");
                        //msg.setHeader("Importance", "High");
                        //msg.setHeader("X-MSMail-Priority", "High");
                        // Set content
                        msg.setText(notice.getMessage());
                        msg.setSentDate(new Date(notice.getNoticeTimeMillis()));
                        if(log.isDebugEnabled()) log.debug("Created Message");
                        Transport.send(msg);
                        if(log.isDebugEnabled()) log.debug("Called Transport.send(Message)");
                    } catch(MessagingException err) {
                        err.printStackTrace();
                    }
                }
            }
        }
    }
}

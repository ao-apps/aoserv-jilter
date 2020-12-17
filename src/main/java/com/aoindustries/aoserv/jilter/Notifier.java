/*
 * aoserv-jilter - Mail filter for the AOServ Platform.
 * Copyright (C) 2007-2013, 2018, 2020  AO Industries, Inc.
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
 * along with aoserv-jilter.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.aoserv.jilter;

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

	final private static Queue<Notice> noticeQueue = new LinkedList<>();

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
				(queueThread = new Thread(new Notifier(), "Notifier")).start();
			}
		}
	}

	@Override
	@SuppressWarnings({"TooBroadCatch", "UseSpecificCatch", "SleepWhileInLoop"})
	public void run() {
		while(true) {
			try {
				while(true) {
					Notice notice;
					synchronized(noticeQueue) {
						while(noticeQueue.isEmpty()) {
							noticeQueue.wait(5 * 60 * 1000);
						}
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
	@SuppressWarnings("AssignmentToForLoopParameter")
	private static void sendNotice(Notice notice) {
		String smtpServer = notice.getSmtpServer();
		// Don't send email when null or empty
		if(smtpServer!=null && (smtpServer=smtpServer.trim()).length()>0) {
			// Try to send to each recipient separately
			for(String to : notice.getTo().split(",")) {
				to = to.trim();
				if(!to.isEmpty()) {
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
						msg.setFrom(new InternetAddress(notice.getFrom(), true));
						msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to, true));
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
						log.error(null, err);
					}
				}
			}
		}
	}
}

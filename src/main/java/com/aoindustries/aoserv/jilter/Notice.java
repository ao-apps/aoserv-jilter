/*
 * aoserv-jilter - Mail filter for the AOServ Platform.
 * Copyright (C) 2007-2011, 2021  AO Industries, Inc.
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

/**
 * Wraps the details that will be sent via a notice.
 *
 * @author  AO Industries, Inc.
 */
public class Notice {

	private final long noticeTimeMillis;
	private final String smtpServer;
	private final String from;
	private final String to;
	private final String subject;
	private final String message;

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

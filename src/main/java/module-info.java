/*
 * aoserv-jilter - Mail filter for the AOServ Platform.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
module com.aoindustries.aoserv.jilter {
  exports com.aoindustries.aoserv.jilter;
  // Direct
  requires com.aoindustries.aoserv.jilter.config; // <groupId>com.aoindustries</groupId><artifactId>aoserv-jilter-config</artifactId>
  requires commons.logging; // <groupId>commons-logging</groupId><artifactId>commons-logging</artifactId>
  requires java.mail; // <groupId>com.sun.mail</groupId><artifactId>javax.mail</artifactId>
  requires jilter; // <groupId>com.sendmail</groupId><artifactId>jilter</artifactId>
  requires org.apache.log4j; // <groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId>
}

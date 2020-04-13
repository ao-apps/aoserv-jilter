/*
 * aoserv-jilter - Mail filter for the AOServ Platform.
 * Copyright (C) 2007-2011, 2018, 2020  AO Industries, Inc.
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

import com.aoindustries.aoserv.jilter.config.JilterConfiguration;
import com.aoindustries.exception.WrappedException;
import com.sendmail.jilter.samples.standalone.SimpleJilterServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
// TODO: Convert to standard Java logging without log4j

/**
 * Runs the jilter server on the primary IP of this machine.
 *
 * @author  AO Industries, Inc.
 */
public class JilterServer {

    private static final Log log = LogFactory.getLog(JilterServer.class);

    public static void main(String[] args) {
        // Initialize log4j
        BasicConfigurator.configure();
        //Logger.getRootLogger().addAppender(new WriterAppender(new SimpleLayout(), System.err));
        Logger.getRootLogger().setLevel(Level.INFO);
        //Category.getRoot().;

        while(true) {
            try {
                start();
                break;
			} catch(ThreadDeath td) {
				throw td;
            } catch(Throwable t) {
                log.error(null, t);
                try {
                    Thread.sleep(10000);
                } catch(InterruptedException e) {
                    log.warn(null, e);
                }
            }
        }
    }

    private static boolean started = false;
    
    public static void start() throws IOException {
        synchronized(System.out) {
            if(!started) {
                try {
                    System.out.print("Starting JilterServer: ");
					JilterConfiguration config = JilterConfiguration.getJilterConfiguration();
                    String ipAddress = config.getListenIP();
                    new Thread(
                        new SimpleJilterServer(
                            new InetSocketAddress(
                                ipAddress,
                                config.getListenPort()
                            ),
							() -> {
								try {
									return new AOJilterHandler();
								} catch(IOException e) {
									throw new WrappedException(e);
								}
							}
                        ),
                        "JilterServer listening on "+ipAddress
                    ).start();
                    started = true;
                    System.out.println("Done");
				} catch(WrappedException e) {
					Throwable cause = e.getCause();
					if(cause instanceof IOException) {
						throw (IOException)cause;
					}
                    throw new IOException(e);
                } catch(ReflectiveOperationException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    private JilterServer() {
    }
}

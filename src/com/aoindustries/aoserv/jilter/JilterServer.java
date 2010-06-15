package com.aoindustries.aoserv.jilter;

/*
 * Copyright 2007-2010 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.jilter.config.JilterConfiguration;
import com.sendmail.jilter.samples.standalone.SimpleJilterServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

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
            } catch(Exception err) {
                log.error(null, err);
                try {
                    Thread.sleep(10000);
                } catch(InterruptedException err2) {
                    log.warn(null, err2);
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
                    String ipAddress = JilterConfiguration.getJilterConfiguration().getPrimaryIP();
                    new Thread(
                        new SimpleJilterServer(
                            new InetSocketAddress(
                                ipAddress,
                                JilterConfiguration.MILTER_PORT
                            ),
                            AOJilterHandler.class.getName()
                        ),
                        "JilterServer listening on "+ipAddress
                    ).start();
                    started = true;
                    System.out.println("Done");
                } catch(ClassNotFoundException err) {
                    IOException ioErr = new IOException("ClassNotFoundException");
                    ioErr.initCause(err);
                    throw ioErr;
                } catch(InstantiationException err) {
                    IOException ioErr = new IOException("InstantiationException");
                    ioErr.initCause(err);
                    throw ioErr;
                } catch(IllegalAccessException err) {
                    IOException ioErr = new IOException("IllegalAccessException");
                    ioErr.initCause(err);
                    throw ioErr;
                }
            }
        }
    }

    private JilterServer() {
    }
}

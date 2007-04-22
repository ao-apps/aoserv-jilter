package com.aoindustries.aoserv.jilter;

/*
 * Copyright 2007 by AO Industries, Inc.,
 * 816 Azalea Rd, Mobile, Alabama, 36693, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.jilter.config.JilterConfiguration;
import com.sendmail.jilter.JilterEOMActions;
import com.sendmail.jilter.JilterHandler;
import com.sendmail.jilter.JilterStatus;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * What does a forwarding look like?  It appears that a forwarding is accepted like any other address as local.  Then sendmail performs
 * the forwarding without a milter check on the way out.
 *
 * @author  AO Industries, Inc.
 */
public class AOJilterHandler implements JilterHandler {

    private static final Log log = LogFactory.getLog(AOJilterHandler.class);

    // The configuration
    JilterConfiguration configuration;

    // connect
    private String hostname;
    private InetAddress hostaddr;
    private String if_addr;
    private String server_name;
    private String if_name;
    private String daemon_name;
    
    // helo
    //private String helohost;

    // envfrom
    private String from;
    private String auth_authen;
    private String mail_host;
    private String auth_ssf;
    private String message_id;
    private String mail_addr;
    private String mail_mailer;
    private String auth_type;

    public AOJilterHandler() throws IOException {
        init();
    }

    private void init() throws IOException {
        // Obtain the configuration once for each use of this filter
        configuration = JilterConfiguration.getJilterConfiguration();

        // connect
        hostname = null;
        hostaddr = null;
        if_addr = null;
        server_name = null;
        if_name = null;
        daemon_name = null;

        // helo
        //helohost = null;

        // envfrom
        from = null;
        auth_authen = null;
        mail_host = null;
        auth_ssf = null;
        message_id = null;
        mail_addr = null;
        mail_mailer = null;
        auth_type = null;
    }

    public int getSupportedProcesses() {
        return PROCESS_CONNECT | /*PROCESS_HELO |*/ PROCESS_ENVFROM | PROCESS_ENVRCPT /*| PROCESS_HEADER | PROCESS_BODY */;
    }

    private void trace(String message) {
        log.trace(System.identityHashCode(this)+ ": " + message);
    }

    private void debug(String message) {
        log.debug(System.identityHashCode(this)+ ": " + message);
    }

    /**
     * Compare to email_smtp_relays table, looking for deny or deny_spam.
     *
     * TODO: Verify against realtime blacklists.
     */
    public JilterStatus connect(String hostname, InetAddress hostaddr, Properties properties) {
        if(log.isTraceEnabled()) {
            trace("connect:");
            trace("    hostname=\""+hostname+"\"");
            trace("    hostaddr=\""+hostaddr.getHostAddress()+"\"");
            trace("    properties:");
            for(Object key : properties.keySet()) {
                trace("        "+key+"=\"" + properties.get(key) + "\"");
            }
        }
        this.hostname = hostname;
        this.hostaddr = hostaddr;
        this.if_addr = properties.getProperty("{if_name}");
        this.server_name = properties.getProperty("j");
        this.if_name = properties.getProperty("{if_name}");
        this.daemon_name = properties.getProperty("{daemon_name}");

        // Look for deny block
        String hostIP = hostaddr.getHostAddress();
        if(configuration.isDenied(hostIP)) {
            JilterStatus status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[] {"Mail from "+hostaddr+" denied."});
            if(log.isTraceEnabled()) trace("connect: returning "+status);
            return status;
        }

        // Look for deny_spam block
        if(configuration.isDeniedSpam(hostIP)) {
            JilterStatus status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[] {"Your mailer ("+hostaddr+") has been reported as sending unsolicited email and has been blocked - please contact AO Industries via (205)454-2556 or postmaster@aoindustries.com"});
            if(log.isTraceEnabled()) trace("connect: returning "+status);
            return status;
        }

        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("connect: returning "+status);
        return status;
    }

    /**
     * This method is currently disabled by <code>getSupportedProcesses</code> and should not be called by sendmail.
     */
    public JilterStatus helo(String helohost, Properties properties) {
        if(log.isTraceEnabled()) {
            trace("helo:");
            trace("    helohost=\""+helohost+"\"");
            trace("    properties:");
            for(Object key : properties.keySet()) {
                trace("        "+key+"=\"" + properties.get(key) + "\"");
            }
        }
        //this.helohost = helohost;
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("helo: returning "+status);
        return status;
    }

    /**
     * <OL>
     *   <LI>Don't allow with blank from addresses.</LI>
     * </OL>
     * TODO: Verify that against SPF / PTR / MX records.
     * TODO: Don't allow outbound to send for an address that doesn't match the IP for the customer
     */
    public JilterStatus envfrom(String[] argv, Properties properties) {
        if(log.isTraceEnabled()) {
            trace("envfrom:");
            for(int c=0;c<argv.length;c++) {
                trace("    argv["+c+"]=\""+argv[c]+"\"");
            }
            trace("    properties:");
            for(Object key : properties.keySet()) {
                trace("        "+key+"=\"" + properties.get(key) + "\"");
            }
        }
        this.from = argv[0];
        this.auth_authen = properties.getProperty("{auth_authen}");
        this.mail_host = properties.getProperty("{mail_host}");
        this.auth_ssf = properties.getProperty("{auth_ssf}");
        this.message_id = properties.getProperty("i");
        this.mail_addr = properties.getProperty("{mail_addr}");
        this.mail_mailer = properties.getProperty("{mail_mailer}");
        this.auth_type = properties.getProperty("{auth_type}");

        JilterStatus status = null; //envfromDontAllowBlankFromAddresses();
        if(status==null) status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("envfrom: returning "+status);
        return status;
    }

    /**
     * <OL>
     *   <LI>If mail going from local to esmtp, then:
     *     <OL type="a">
     *       <LI>Don't allow empty from address</LI>
     *       <LI>If this ao_server has "restrict_outbound_email" set to true: Make sure from address is a valid address on this machine</LI>
     *     </OL>
     *   </LI>
     *   <LI>If mail going from local to local, then:
     *     <OL type="a">
     *       <LI>Make sure recipient is a valid email address on this machine</LI>
     *     </OL>
     *   </LI>
     *   <LI>If mail going from esmtp to esmtp, then:
     *     <OL type="a">
     *       <LI>Make sure hostaddr is one of IP addresses of this machine OR relaying has been allowed from that IP</LI>
     *       <LI>Don't allow empty from address</LI>
     *       <LI>Make sure from address is a valid address on this machine</LI>
     *     </OL>
     *   </LI>
     *   <LI>If mail going from esmtp to local, then:
     *     <OL type="a">
     *       <LI>Make sure recipient is a valid email address on this machine</LI>
     *     </OL>
     *   </LI>
     *   <LI>If mail going from auth to esmtp, then:
     *     <OL type="a">
     *       <LI>Make sure authenticated</LI>
     *       <LI>Don't allow empty from address</LI>
     *       <LI>Make sure from address is a valid address on this machine</LI>
     *     </OL>
     *   </LI>
     *   <LI>If mail going from auth to local, then:
     *     <OL type="a">
     *       <LI>Make sure recipient is a valid email address on this machine</LI>
     *     </OL>
     *   </LI>
     *   <LI>If any other pattern, then:
     *     <OL type="a">
     *       <LI>Return failure code and description</LI>
     *     </OL>
     *   </LI>
     * </OL>
     * TODO: Limit inbound email rate
     */
    public JilterStatus envrcpt(String[] argv, Properties properties) {
        try {
            if(log.isTraceEnabled()) {
                trace("envrcpt:");
                for(int c=0;c<argv.length;c++) {
                    trace("    argv["+c+"]=\""+argv[c]+"\"");
                }
                trace("    properties:");
                for(Object key : properties.keySet()) {
                    trace("        "+key+"=\"" + properties.get(key) + "\"");
                }
            }

            String to = argv[0];
            String rcpt_host = properties.getProperty("{rcpt_host}");
            String rcpt_mailer = properties.getProperty("{rcpt_mailer}");
            String rcpt_addr = properties.getProperty("{rcpt_addr}");

            boolean isFromLocal;
            boolean isFromAuth;
            boolean isFromEsmtp;
            if("local".equals(mail_mailer)) {
                // It is "local" if the hostaddr is one of the IP addresses of this machine
                // It is "auth" if the hostaddr is not one of the IP addresses of this machine - whether they are actually logged in is checked below
                boolean found = isHostAddrLocal();
                isFromLocal = found;
                isFromAuth = !found;
                isFromEsmtp = false;
            } else if("esmtp".equals(mail_mailer)) {
                // If is "esmtp" if not authenticated
                isFromLocal = false;
                isFromAuth = auth_authen!=null && auth_authen.length()>0;
                isFromEsmtp = !isFromAuth;
            } else {
                JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"Unexpected mail_mailer: "+mail_mailer});
                if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                return status;
            }
            boolean isToLocal = "local".equals(rcpt_mailer);
            boolean isToEsmtp = "esmtp".equals(rcpt_mailer);

            if(log.isTraceEnabled()) {
                trace("envrcpt: isFromLocal="+isFromLocal);
                trace("envrcpt: isFromAuth="+isFromAuth);
                trace("envrcpt: isFromEsmtp="+isFromEsmtp);
                trace("envrcpt: isToLocal="+isToLocal);
                trace("envrcpt: isToEsmtp="+isToEsmtp);
            }
            if(isFromLocal) {
                if(isToEsmtp) {
                    // Mail going from local to esmtp
                    JilterStatus status = null;

                    // Don't allow empty from address
                    if(from==null || from.length()<2 || from.equals("<>")) {
                        status = JilterStatus.makeCustomStatus("550", "5.1.7", new String[] {"local: Email not accepted with an empty from address."});
                    }

                    // If this ao_server has "restrict_outbound_email" set to true: Make sure from address is a valid address on this machine
                    if(configuration.getRestrictOutboundEmail()) status = checkFromIsLocal();

                    // Otherwise, continue
                    if(status==null) status = JilterStatus.SMFIS_CONTINUE;
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                } else if(isToLocal) {
                    // Mail going from local to local
                    JilterStatus status = null;

                    // Make sure recipient is a valid email address on this machine
                    status = checkToIsLocal(to);

                    // Otherwise, continue
                    if(status==null) status = JilterStatus.SMFIS_CONTINUE;
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                } else {
                    JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"Unexpected rcpt_mailer: "+rcpt_mailer});
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                }
            } else if(isFromEsmtp) {
                if(isToEsmtp) {
                    // Mail going from esmtp to esmtp
                    JilterStatus status = null;

                    // Make sure hostaddr is one of IP addresses of this machine OR relaying has been allowed from that IP
                    boolean isHostAddrLocal = isHostAddrLocal();
                    boolean isHostAddrRelayingAllowed = isHostAddrRelayingAllowed();
                    if(log.isTraceEnabled()) {
                        trace("envrcpt: isHostAddrLocal="+isHostAddrLocal);
                        trace("envrcpt: isHostAddrRelayingAllowed="+isHostAddrRelayingAllowed);
                    }
                    if(
                        !isHostAddrLocal
                        && !isHostAddrRelayingAllowed
                    ) status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[] {"esmtp: Relaying denied. Proper authentication required."});

                    // Don't allow empty from address
                    if(from==null || from.length()<2 || from.equals("<>")) {
                        status = JilterStatus.makeCustomStatus("550", "5.1.7", new String[] {"esmtp: Email not accepted with an empty from address."});
                    }

                    // Make sure from address is a valid address on this machine
                    if(status==null) {
                        if(!isHostAddrLocal || configuration.getRestrictOutboundEmail()) status = checkFromIsLocal();
                    }

                    // Otherwise, continue
                    if(status==null) status = JilterStatus.SMFIS_CONTINUE;
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                } else if(isToLocal) {
                    // Mail going from esmtp to local
                    JilterStatus status = null;

                    // Make sure recipient is a valid email address on this machine
                    status = checkToIsLocal(to);

                    // Otherwise, continue
                    if(status==null) status = JilterStatus.SMFIS_CONTINUE;
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                } else {
                    JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"Unexpected rcpt_mailer: "+rcpt_mailer});
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                }
            } else if(isFromAuth) {
                if(isToEsmtp) {
                    // Mail going from auth to esmtp
                    JilterStatus status = null;

                    // Make sure authenticated
                    if(auth_authen==null || auth_authen.length()==0) status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[] {"auth: Relaying denied. Proper authentication required."});

                    // Don't allow empty from address
                    if(from==null || from.length()<2 || from.equals("<>")) {
                        status = JilterStatus.makeCustomStatus("550", "5.1.7", new String[] {"auth: Email not accepted with an empty from address."});
                    }

                    // Make sure from address is a valid address on this machine
                    status = checkFromIsLocal();

                    // Otherwise, continue
                    if(status==null) status = JilterStatus.SMFIS_CONTINUE;
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                } else if(isToLocal) {
                    // Mail going from auth to local
                    JilterStatus status = null;

                    // Make sure recipient is a valid email address on this machine
                    status = checkToIsLocal(to);

                    // Otherwise, continue
                    if(status==null) status = JilterStatus.SMFIS_CONTINUE;
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                } else {
                    JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"Unexpected rcpt_mailer: "+rcpt_mailer});
                    if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                    return status;
                }
            } else {
                JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"Unexpected mail_mailer: "+mail_mailer});
                if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
                return status;
            }
        } catch(IOException err) {
            if(log.isErrorEnabled()) log.error(null, err);
            JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"java.io.IOException"});
            if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
            return status;
        } catch(SQLException err) {
            if(log.isErrorEnabled()) log.error(null, err);
            JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[] {"java.io.SQLException"});
            if(log.isTraceEnabled()) trace("envrcpt: returning "+status);
            return status;
        }
    }

    /**
     * TODO: Check for X-Loop here instead of procmail for more performance.
     * TODO: Reenable header in other parts of this code to activate this method.
     */
    public JilterStatus header(String headerf, String headerv) {
        if(log.isTraceEnabled()) {
            trace("header:");
            trace("    headerf=\""+headerf+"\"");
            trace("    headerv=\""+headerv+"\"");
        }
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("header: returning "+status);
        return status;
    }

    public JilterStatus eoh() {
        if(log.isTraceEnabled()) {
            trace("eoh:");
        }
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("eoh: returning "+status);
        return status;
    }

    /**
     * TODO: Call SpamAssassin from here
     * TODO: Limit inbox size here
     * TODO: Reenable body in other parts of this code to activate this method.
     */
    public JilterStatus body(ByteBuffer bodyp) {
        if(log.isTraceEnabled()) {
            trace("body:");
        }
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("body: returning "+status);
        return status;
    }

    public JilterStatus eom(JilterEOMActions eomActions, Properties properties) {
        if(log.isTraceEnabled()) {
            trace("eom:");
        }
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("eom: returning "+status);
        return status;
    }

    public JilterStatus abort() {
        if(log.isTraceEnabled()) {
            trace("abort:");
        }
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("abort: returning "+status);
        return status;
    }

    public JilterStatus close() {
        try {
            if(log.isTraceEnabled()) {
                trace("close:");
            }
            init();
        } catch(IOException err) {
            if(log.isErrorEnabled()) log.error("IOException when closing ignored.", err);
        }
        JilterStatus status = JilterStatus.SMFIS_CONTINUE;
        if(log.isTraceEnabled()) trace("close: returning "+status);
        return status;
    }

    public int getRequiredModifications() {
        if(log.isTraceEnabled()) {
            trace("getRequiredModifications:");
        }
        if(log.isTraceEnabled()) trace("getRequiredModifications: returning SMFIF_NONE");
        return SMFIF_NONE;
    }
    
    /*
     * The specific filters follow.
     */
    
    /**
     * Determines if the current hostaddr is local.
     */
    protected boolean isHostAddrLocal() throws IOException, SQLException {
        String hostIP = hostaddr.getHostAddress();
        return configuration.isLocalIPAddress(hostIP);
    }

    /**
     * Checks if relaying has been allowed from hostaddr
     */
    protected boolean isHostAddrRelayingAllowed() throws IOException, SQLException {
        String hostIP = hostaddr.getHostAddress();
        return configuration.isAllowRelay(hostIP);
    }

    /**
     * Makes sure the from address is a valid address on this machine.
     *
     * @return <code>null</code> if passed or <code>JilterStatus</code> for not allowed.
     */
    protected JilterStatus checkFromIsLocal() throws IOException, SQLException {
        String parsedFrom = from;

        // Trim the < and > from the from address
        if(
            parsedFrom.length()>=2
            && parsedFrom.charAt(0)=='<'
            && parsedFrom.charAt(parsedFrom.length()-1)=='>'
        ) parsedFrom = parsedFrom.substring(1, parsedFrom.length()-1);

        // Find the last @ in the address
        int atPos = parsedFrom.lastIndexOf('@');
        if(atPos==-1) return JilterStatus.makeCustomStatus("550", "5.1.7", new String[] {"The from address "+from+" must contain both address and domain in the form address@domain, the symbol @ was not found."});

        String domain = parsedFrom.substring(atPos+1);
        if(domain.length()==0) return JilterStatus.makeCustomStatus("550", "5.1.8", new String[] {"The from address "+from+" must contain both address and domain in the form address@domain, nothing was provided after the @ symbol."});

        String address = parsedFrom.substring(0, atPos);
        if(address.length()==0) return JilterStatus.makeCustomStatus("550", "5.1.7", new String[] {"The from address "+from+" must contain both address and domain in the form address@domain, nothing was provided before the @ symbol."});

        Set<String> addresses = configuration.getAddresses(domain);
        if(addresses==null) return JilterStatus.makeCustomStatus("550", "5.1.8", new String[] {"The from address "+from+" is not allowed. This server does not receive email for "+domain});

        if(!addresses.contains(address.toLowerCase())) return JilterStatus.makeCustomStatus("550", "5.1.7", new String[] {"The from address "+from+" does not exist on this server."});

        return null;
    }

    /**
     * Makes sure the to address is a valid address on this machine.
     *
     * @return <code>null</code> if passed or <code>JilterStatus</code> for not allowed.
     */
    protected JilterStatus checkToIsLocal(String to) throws IOException, SQLException {
        String parsedTo = to;

        // Trim the < and > from the to address
        if(
            parsedTo.length()>=2
            && parsedTo.charAt(0)=='<'
            && parsedTo.charAt(parsedTo.length()-1)=='>'
        ) parsedTo = parsedTo.substring(1, parsedTo.length()-1);

        // Find the last @ in the address
        int atPos = parsedTo.lastIndexOf('@');
        if(atPos==-1) return JilterStatus.makeCustomStatus("550", "5.1.3", new String[] {"The recipient address "+to+" must contain both address and domain in the form address@domain, the symbol @ was not found."});

        String domain = parsedTo.substring(atPos+1);
        if(domain.length()==0) return JilterStatus.makeCustomStatus("550", "5.1.2", new String[] {"The recipient address "+to+" must contain both address and domain in the form address@domain, nothing was provided after the @ symbol."});

        String address = parsedTo.substring(0, atPos);
        if(address.length()==0) return JilterStatus.makeCustomStatus("550", "5.1.1", new String[] {"The recipient address "+to+" must contain both address and domain in the form address@domain, nothing was provided before the @ symbol."});

        Set<String> addresses = configuration.getAddresses(domain);
        if(addresses==null) return JilterStatus.makeCustomStatus("550", "5.1.2", new String[] {"The recipient address "+to+" does not exist on this server. This server does not receive email for "+domain});

        // These addresses are always deliverable
        if(
            "abuse".equalsIgnoreCase(address)
            || "devnull".equalsIgnoreCase(address)
            || "mailer-daemon".equalsIgnoreCase(address)
            || "postmaster".equalsIgnoreCase(address)
        ) return null;

        // Look for an exact match
        boolean found = addresses.contains(address.toLowerCase());

        // Also accept wildcards
        if(!found) found = addresses.contains("");

        // If not found, return 5.1.1
        if(!found) return JilterStatus.makeCustomStatus("550", "5.1.1", new String[] {"The recipient address "+to+" does not exist on this server."});

        return null;
    }
}

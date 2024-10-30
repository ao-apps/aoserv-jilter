/*
 * aoserv-jilter - Mail filter for the AOServ Platform.
 * Copyright (C) 2007-2013, 2020, 2021, 2022, 2024  AO Industries, Inc.
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
import com.aoindustries.aoserv.jilter.config.JilterConfiguration;
import com.sendmail.jilter.JilterEOMActions;
import com.sendmail.jilter.JilterHandler;
import com.sendmail.jilter.JilterStatus;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * What does a forwarding look like?  It appears that a forwarding is accepted like any other address as local.  Then sendmail performs
 * the forwarding without a milter check on the way out.
 *
 * <p>TODO: EmailAttachmentType filters - go into .zip, .tar, .tgz, .tar.gz, ..., too.</p>
 *
 * @author  AO Industries, Inc.
 */
public class AoservJilterHandler implements JilterHandler {

  /**
   * When <code>true</code>, notices will be sent but emails will not be stopped.
   */
  public static final boolean NOTIFY_ONLY_MODE = false;

  /**
   * Outbound emails to these addresses are never limited and are never added to counters.
   * This is to make sure notices from critical system processes are not blocked.
   */
  private static final String[] noLimitToAddresses = {
      "aoserv@aoindustries.com",
      "2054542556@tmomail.net",
      //"2514584757@tmomail.net",
      "support@aoindustries.com"
  };

  private static final Log log = LogFactory.getLog(AoservJilterHandler.class);

  /**
   * Keeps a cache of the inbound email counters on a per-business basis.
   */
  private static final Map<String, EmailCounter> counterInCache = new HashMap<>();

  /**
   * Gets the inbound email counter for the provided business, or <code>null</code> if its inbound
   * email is not limited.
   */
  public static EmailCounter getInCounter(JilterConfiguration configuration, String accounting) {
    EmailLimit emailLimit = configuration.getEmailInLimit(accounting);
    if (emailLimit == null) {
      return null;
    }
    synchronized (counterInCache) {
      EmailCounter emailCounter = counterInCache.get(accounting);
      // Recreate if doesn't exist or settings changed
      if (emailCounter == null || !emailCounter.getEmailLimit().equals(emailLimit)) {
        emailCounter = new EmailCounter(accounting, emailLimit);
        counterInCache.put(accounting, emailCounter);
      }
      return emailCounter;
    }
  }

  /**
   * Keeps a cache of the outbound email counters on a per-business basis.
   */
  private static final Map<String, EmailCounter> counterOutCache = new HashMap<>();

  /**
   * Gets the outbound email counter for the provided business, or <code>null</code> if its outbound
   * email is not limited.
   */
  public static EmailCounter getOutCounter(JilterConfiguration configuration, String accounting) {
    EmailLimit emailLimit = configuration.getEmailOutLimit(accounting);
    if (emailLimit == null) {
      return null;
    }
    synchronized (counterOutCache) {
      EmailCounter emailCounter = counterOutCache.get(accounting);
      // Recreate if doesn't exist or settings changed
      if (emailCounter == null || !emailCounter.getEmailLimit().equals(emailLimit)) {
        emailCounter = new EmailCounter(accounting, emailLimit);
        counterOutCache.put(accounting, emailCounter);
      }
      return emailCounter;
    }
  }

  /**
   * Keeps a cache of the relay email counters on a per-business basis.
   */
  private static final Map<String, EmailCounter> counterRelayCache = new HashMap<>();

  /**
   * Gets the relay email counter for the provided business, or <code>null</code> if its relay
   * email is not limited.
   */
  public static EmailCounter getRelayCounter(JilterConfiguration configuration, String accounting) {
    EmailLimit emailLimit = configuration.getEmailRelayLimit(accounting);
    if (emailLimit == null) {
      return null;
    }
    synchronized (counterRelayCache) {
      EmailCounter emailCounter = counterRelayCache.get(accounting);
      // Recreate if doesn't exist or settings changed
      if (emailCounter == null || !emailCounter.getEmailLimit().equals(emailLimit)) {
        emailCounter = new EmailCounter(accounting, emailLimit);
        counterRelayCache.put(accounting, emailCounter);
      }
      return emailCounter;
    }
  }

  private enum CounterMode {
    IN, OUT, RELAY
  }

  /**
   * Gets the counter for the provided mode and business or <code>null</code> if it is not limited.
   */
  static EmailCounter getCounter(JilterConfiguration configuration, String accounting, CounterMode mode) {
    switch (mode) {
      case IN:
        return getInCounter(configuration, accounting);
      case OUT:
        return getOutCounter(configuration, accounting);
      case RELAY:
        return getRelayCounter(configuration, accounting);
      default:
        throw new IllegalArgumentException("Unexpected mode: " + mode);
    }
  }

  // The configuration
  JilterConfiguration configuration;

  // connect
  //private String hostname;
  private InetAddress hostaddr;
  //private String ifAddr;
  //private String serverName;
  //private String ifName;
  //private String daemonName;

  // helo
  //private String helohost;

  // envfrom
  private String from;
  private String authAuthen;
  //private String mailHost;
  //private String authSsf;
  //private String messageId;
  //private String mailAddr;
  private String mailMailer;

  //private String authType;

  /**
   * Creates a new {@link AoservJilterHandler}.
   */
  public AoservJilterHandler() throws IOException {
    init();
  }

  private void init() throws IOException {
    // Obtain the configuration once for each use of this filter
    configuration = JilterConfiguration.getJilterConfiguration();

    // connect
    //hostname = null;
    hostaddr = null;
    //ifAddr = null;
    //serverName = null;
    //ifName = null;
    //daemonName = null;

    // helo
    //helohost = null;

    // envfrom
    from = null;
    authAuthen = null;
    //mailHost = null;
    //authSsf = null;
    //messageId = null;
    //mailAddr = null;
    mailMailer = null;
    //authType = null;
  }

  @Override
  public int getSupportedProcesses() {
    return PROCESS_CONNECT | /*PROCESS_HELO |*/ PROCESS_ENVFROM | PROCESS_ENVRCPT /*| PROCESS_HEADER | PROCESS_BODY */;
  }

  private void trace(String message) {
    if (log.isTraceEnabled()) {
      log.trace(System.identityHashCode(this) + ": " + message);
    }
  }

  private void debug(String message) {
    if (log.isDebugEnabled()) {
      log.debug(System.identityHashCode(this) + ": " + message);
    }
  }

  /**
   * Compare to email_smtp_relays table, looking for deny or deny_spam.
   *
   * <p>TODO: Verify against realtime blacklists.</p>
   */
  @Override
  public JilterStatus connect(String hostname, InetAddress hostaddr, Properties properties) {
    if (log.isTraceEnabled()) {
      trace("connect:");
      trace("    hostname=\"" + hostname + "\"");
      trace("    hostaddr=\"" + hostaddr.getHostAddress() + "\"");
      trace("    properties:");
      for (Object key : properties.keySet()) {
        trace("        " + key + "=\"" + properties.get(key) + "\"");
      }
    }
    //this.hostname = hostname;
    this.hostaddr = hostaddr;
    //this.ifAddr = properties.getProperty("{if_name}"); // Should be "if_addr"?
    //this.serverName = properties.getProperty("j");
    //this.ifName = properties.getProperty("{if_name}");
    //this.daemonName = properties.getProperty("{daemon_name}");

    // Look for deny block
    String hostIp = hostaddr.getHostAddress();
    if (configuration.isDenied(hostIp)) {
      JilterStatus status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[]{"Mail from " + hostaddr.getHostAddress() + " denied."});
      if (log.isTraceEnabled()) {
        trace("connect: returning " + status);
      }
      return status;
    }

    // Look for deny_spam block
    if (configuration.isDeniedSpam(hostIp)) {
      JilterStatus status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[]{"Your mailer ("
          + hostaddr.getHostAddress() + ") has been reported as sending unsolicited email and has been blocked - please contact AO Industries via (205)454-2556 or postmaster@aoindustries.com"});
      if (log.isTraceEnabled()) {
        trace("connect: returning " + status);
      }
      return status;
    }

    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("connect: returning " + status);
    }
    return status;
  }

  /**
   * This method is currently disabled by <code>getSupportedProcesses</code> and should not be called by sendmail.
   */
  @Override
  public JilterStatus helo(String helohost, Properties properties) {
    if (log.isTraceEnabled()) {
      trace("helo:");
      trace("    helohost=\"" + helohost + "\"");
      trace("    properties:");
      for (Object key : properties.keySet()) {
        trace("        " + key + "=\"" + properties.get(key) + "\"");
      }
    }
    //this.helohost = helohost;
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("helo: returning " + status);
    }
    return status;
  }

  /**
   * TODO: Verify that against SPF / PTR / MX records.
   * TODO: Don't allow outbound to send for an address that doesn't match the IP for the customer (virtual hosting IP enforcement)
   */
  @Override
  public JilterStatus envfrom(String[] argv, Properties properties) {
    if (log.isTraceEnabled()) {
      trace("envfrom:");
      for (int c = 0; c < argv.length; c++) {
        trace("    argv[" + c + "]=\"" + argv[c] + "\"");
      }
      trace("    properties:");
      for (Object key : properties.keySet()) {
        trace("        " + key + "=\"" + properties.get(key) + "\"");
      }
    }
    this.from = argv[0];
    this.authAuthen = properties.getProperty("{auth_authen}");
    //this.mailHost = properties.getProperty("{mail_host}");
    //this.authSsf = properties.getProperty("{auth_ssf}");
    //this.messageId = properties.getProperty("i");
    //this.mailAddr = properties.getProperty("{mail_addr}");
    this.mailMailer = properties.getProperty("{mail_mailer}");
    //this.authType = properties.getProperty("{auth_type}");

    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("envfrom: returning " + status);
    }
    return status;
  }

  /**
   * {@inheritDoc}
   * <ol>
   *   <li>If mail going from local to esmtp, then:
   *     <ol type="a">
   *       <li>Don't allow empty from address</li>
   *       <li>If this ao_server has "restrict_outbound_email" set to true: Make sure from address is a valid address on this machine</li>
   *       <li>Limit as outgoing mail (use noLimitToAddresses)</li>
   *     </ol>
   *   </li>
   *   <li>If mail going from local to local, then:
   *     <ol type="a">
   *       <li>Make sure recipient is a valid email address on this machine</li>
   *       <li>Do not limit the email</li>
   *     </ol>
   *   </li>
   *   <li>If mail going from esmtp to esmtp, then:
   *     <ol type="a">
   *       <li>Make sure hostaddr is one of IP addresses of this machine OR relaying has been allowed from that IP</li>
   *       <li>Don't allow empty from address</li>
   *       <li>Make sure from address is a valid address on this machine</li>
   *       <li>Limit as outgoing (use noLimitToAddresses) if hostaddr is on this machine OR limit as relay if from an outside IP</li>
   *     </ol>
   *   </li>
   *   <li>If mail going from esmtp to local, then:
   *     <ol type="a">
   *       <li>Make sure recipient is a valid email address on this machine</li>
   *       <li>Limit as incoming mail</li>
   *     </ol>
   *   </li>
   *   <li>If mail going from auth to esmtp, then:
   *     <ol type="a">
   *       <li>Make sure authenticated</li>
   *       <li>Don't allow empty from address</li>
   *       <li>Make sure from address is a valid address on this machine</li>
   *       <li>Limit as outgoing (use noLimitToAddresses) if hostaddr is on this machine OR limit as relay if from an outside IP</li>
   *     </ol>
   *   </li>
   *   <li>If mail going from auth to local, then:
   *     <ol type="a">
   *       <li>Make sure recipient is a valid email address on this machine</li>
   *       <li>Limit as incoming mail</li>
   *     </ol>
   *   </li>
   *   <li>If any other pattern, then:
   *     <ol type="a">
   *       <li>Return failure code and description</li>
   *     </ol>
   *   </li>
   * </ol>
   */
  @Override
  public JilterStatus envrcpt(String[] argv, Properties properties) {
    if (log.isTraceEnabled()) {
      trace("envrcpt:");
      for (int c = 0; c < argv.length; c++) {
        trace("    argv[" + c + "]=\"" + argv[c] + "\"");
      }
      trace("    properties:");
      for (Object key : properties.keySet()) {
        trace("        " + key + "=\"" + properties.get(key) + "\"");
      }
    }

    String to = argv[0];
    String rcptHost = properties.getProperty("{rcpt_host}");
    String rcptMailer = properties.getProperty("{rcpt_mailer}");
    String rcptAddr = properties.getProperty("{rcpt_addr}");

    boolean isFromLocal;
    boolean isFromAuth;
    boolean isFromEsmtp;
    switch (mailMailer) {
      case "local":
        {
          // It is "local" if the hostaddr is one of the IP addresses of this machine
          // It is "auth" if the hostaddr is not one of the IP addresses of this machine - whether they are actually logged in is checked below
          boolean isLocalIp = isHostAddrLocal();
          if (isLocalIp) {
            isFromLocal = true;
            isFromAuth = false;
            isFromEsmtp = false;
          } else {
            boolean isRelayAllowed = isHostAddrRelayingAllowed();
            if (isRelayAllowed) {
              isFromLocal = false;
              isFromAuth = false;
              isFromEsmtp = true;
            } else {
              isFromLocal = false;
              isFromAuth = true;
              isFromEsmtp = false;
            }
          }
          break;
        }
      case "esmtp":
        {
          // If is "esmtp" if not authenticated
          isFromLocal = false;
          isFromAuth = authAuthen != null && authAuthen.length() > 0;
          isFromEsmtp = !isFromAuth;
          break;
        }
      default:
        {
          JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[]{"Unexpected mail_mailer: " + mailMailer});
          if (log.isTraceEnabled()) {
            trace("envrcpt: returning " + status);
          }
          return status;
        }
    }
    boolean isToLocal = "local".equals(rcptMailer);
    boolean isToEsmtp = "esmtp".equals(rcptMailer);

    if (log.isTraceEnabled()) {
      trace("envrcpt: isFromLocal=" + isFromLocal);
      trace("envrcpt: isFromAuth=" + isFromAuth);
      trace("envrcpt: isFromEsmtp=" + isFromEsmtp);
      trace("envrcpt: isToLocal=" + isToLocal);
      trace("envrcpt: isToEsmtp=" + isToEsmtp);
    }
    if (isFromLocal) {
      if (isToEsmtp) {
        // Mail going from local to esmtp
        JilterStatus status = null;

        // Don't allow empty from address
        if (status == null) {
          if (from == null || from.length() < 2 || "<>".equals(from)) {
            status = JilterStatus.makeCustomStatus("550", "5.1.7", new String[]{"local: Email not accepted with an empty from address."});
          }
        }

        // If this ao_server has "restrict_outbound_email" set to true: Make sure from address is a valid address on this machine
        if (status == null) {
          if (configuration.getRestrictOutboundEmail()) {
            status = checkFromIsLocal();
          }
        }

        // Limit as outgoing mail (use noLimitToAddresses)
        if (status == null) {
          if (
              !isNoLimitAddress(to)
                  && isLimited(CounterMode.OUT, from)
          ) {
            status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"local: Outgoing email limit reached, throttling additional emails"});
          }
        }

        // Otherwise, continue
        if (status == null) {
          status = JilterStatus.SMFIS_CONTINUE;
        }
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      } else if (isToLocal) {
        // Mail going from local to local
        JilterStatus status = null;

        // Make sure recipient is a valid email address on this machine
        if (status == null) {
          status = checkToIsLocal(to);
        }

        // Otherwise, continue
        if (status == null) {
          status = JilterStatus.SMFIS_CONTINUE;
        }
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      } else {
        JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[]{"Unexpected rcpt_mailer: " + rcptMailer});
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      }
    } else if (isFromEsmtp) {
      if (isToEsmtp) {
        // Mail going from esmtp to esmtp
        JilterStatus status = null;

        // Make sure hostaddr is one of IP addresses of this machine OR relaying has been allowed from that IP
        boolean isHostAddrLocal = isHostAddrLocal();
        boolean isHostAddrRelayingAllowed = isHostAddrRelayingAllowed();
        if (log.isTraceEnabled()) {
          trace("envrcpt: isHostAddrLocal=" + isHostAddrLocal);
          trace("envrcpt: isHostAddrRelayingAllowed=" + isHostAddrRelayingAllowed);
        }
        if (status == null) {
          if (
              !isHostAddrLocal
                  && !isHostAddrRelayingAllowed
          ) {
            status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[]{"esmtp: Relaying from "
                + hostaddr.getHostAddress() + " denied. Proper authentication required."});
          }
        }

        // Don't allow empty from address
        if (status == null) {
          if (from == null || from.length() < 2 || "<>".equals(from)) {
            status = JilterStatus.makeCustomStatus("550", "5.1.7", new String[]{"esmtp: Email not accepted with an empty from address."});
          }
        }

        // Make sure from address is a valid address on this machine
        if (status == null) {
          if (!isHostAddrLocal || configuration.getRestrictOutboundEmail()) {
            status = checkFromIsLocal();
          }
        }

        // Limit as outgoing (use noLimitToAddresses) if hostaddr is on this machine OR limit as relay if from an outside IP
        if (status == null) {
          if (isHostAddrLocal) {
            // Limit as outgoing (use noLimitToAddresses)
            if (
                !isNoLimitAddress(to)
                    && isLimited(CounterMode.OUT, from)
            ) {
              status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"esmtp: Outgoing email limit reached, throttling additional emails"});
            }
          } else {
            // Limit as relay
            if (isLimited(CounterMode.RELAY, from)) {
              status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"esmtp: Relay email limit reached, throttling additional emails"});
            }
          }
        }

        // Otherwise, continue
        if (status == null) {
          status = JilterStatus.SMFIS_CONTINUE;
        }
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      } else if (isToLocal) {
        // Mail going from esmtp to local
        JilterStatus status = null;

        // Make sure recipient is a valid email address on this machine
        if (status == null) {
          status = checkToIsLocal(to);
        }

        // Limit as incoming mail
        if (status == null) {
          if (isLimited(CounterMode.IN, to)) {
            status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"esmtp: Incoming email limit reached, throttling additional emails"});
          }
        }

        // Otherwise, continue
        if (status == null) {
          status = JilterStatus.SMFIS_CONTINUE;
        }
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      } else {
        JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[]{"Unexpected rcpt_mailer: " + rcptMailer});
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      }
    } else if (isFromAuth) {
      if (isToEsmtp) {
        // Mail going from auth to esmtp
        JilterStatus status = null;

        // Make sure authenticated
        if (status == null) {
          if (authAuthen == null || authAuthen.length() == 0) {
            status = JilterStatus.makeCustomStatus("550", "5.7.1", new String[]{"auth: Relaying from "
                + hostaddr.getHostAddress() + " denied. Proper authentication required."});
          }
        }

        // Don't allow empty from address
        if (status == null) {
          if (from == null || from.length() < 2 || "<>".equals(from)) {
            status = JilterStatus.makeCustomStatus("550", "5.1.7", new String[]{"auth: Email not accepted with an empty from address."});
          }
        }

        // Make sure from address is a valid address on this machine
        if (status == null) {
          status = checkFromIsLocal();
        }

        // Limit as outgoing (use noLimitToAddresses) if hostaddr is on this machine OR limit as relay if from an outside IP
        if (status == null) {
          boolean isHostAddrLocal = isHostAddrLocal();
          if (isHostAddrLocal) {
            // Limit as outgoing (use noLimitToAddresses)
            if (
                !isNoLimitAddress(to)
                    && isLimited(CounterMode.OUT, from)
            ) {
              status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"auth: Outgoing email limit reached, throttling additional emails"});
            }
          } else {
            // Limit as relay
            if (isLimited(CounterMode.RELAY, from)) {
              status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"auth: Relay email limit reached, throttling additional emails"});
            }
          }
        }

        // Otherwise, continue
        if (status == null) {
          status = JilterStatus.SMFIS_CONTINUE;
        }
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      } else if (isToLocal) {
        // Mail going from auth to local
        JilterStatus status = null;

        // Make sure recipient is a valid email address on this machine
        if (status == null) {
          status = checkToIsLocal(to);
        }

        // Limit as incoming mail
        if (status == null) {
          if (isLimited(CounterMode.IN, to)) {
            status = JilterStatus.makeCustomStatus("450", "4.3.2", new String[]{"auth: Incoming email limit reached, throttling additional emails"});
          }
        }

        // Otherwise, continue
        if (status == null) {
          status = JilterStatus.SMFIS_CONTINUE;
        }
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      } else {
        JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[]{"Unexpected rcpt_mailer: " + rcptMailer});
        if (log.isTraceEnabled()) {
          trace("envrcpt: returning " + status);
        }
        return status;
      }
    } else {
      JilterStatus status = JilterStatus.makeCustomStatus("451", "4.3.0", new String[]{"Unexpected mail_mailer: " + mailMailer});
      if (log.isTraceEnabled()) {
        trace("envrcpt: returning " + status);
      }
      return status;
    }
  }

  /**
   * Strips characters between first plus (at position >= 1) and the first @ symbol.
   */
  private static String stripPlusAddress(String address) {
    int plusPos = address.indexOf('+', 1);
    if (plusPos == -1) {
      return address;
    }
    int atPos = address.indexOf('@');
    if (atPos == -1) {
      return address;
    }
    if (plusPos > atPos) {
      return address;
    }
    return address.substring(0, plusPos) + address.substring(atPos);
  }

  /**
   * Determines if the provided to address is one that should not be limited.
   *
   * @see  #noLimitToAddresses
   */
  private boolean isNoLimitAddress(String to) {
    String parsedTo = to;

    // Trim the < and > from the to address
    if (
        parsedTo.length() >= 2
            && parsedTo.charAt(0) == '<'
            && parsedTo.charAt(parsedTo.length() - 1) == '>'
    ) {
      parsedTo = parsedTo.substring(1, parsedTo.length() - 1);
    }

    parsedTo = stripPlusAddress(parsedTo);

    // Case-insensitive match
    for (String noLimitToAddress : noLimitToAddresses) {
      if (noLimitToAddress.equalsIgnoreCase(parsedTo)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines if the email should be limited by maintaining counters and timing.  Will also queue notification emails
   * (sent in a background Thread) when the limit has been reached.
   *
   * @return  true if the email should not be allowed, false if it should be allowed
   */
  private boolean isLimited(CounterMode mode, String address) {
    // Trim the < and > from the to address
    if (
        address.length() >= 2
            && address.charAt(0) == '<'
            && address.charAt(address.length() - 1) == '>'
    ) {
      address = address.substring(1, address.length() - 1);
    }

    address = stripPlusAddress(address);

    // Find the last @ in the address
    int atPos = address.lastIndexOf('@');
    if (atPos == -1) {
      // Other filters should catch this, return false
      return false;
    }

    String domain = address.substring(atPos + 1);
    if (domain.length() == 0) {
      // Other filters should catch this, return false
      return false;
    }

    // Determine the business name from the domain
    String accounting = configuration.getBusiness(domain);
    if (accounting == null) {
      // Other filters should catch this, return false
      return false;
    }

    EmailCounter counter = getCounter(configuration, accounting, mode);
    if (counter == null) {
      // Not limited, return false
      return false;
    }
    EmailLimit emailLimit = counter.getEmailLimit();

    long currentTimeMillis = System.currentTimeMillis();
    synchronized (counter) {
      // Decrement the counter based on the time since the last decrement
      long lastDecrementTime = counter.getLastDecrementTime();
      if (log.isTraceEnabled()) {
        log.trace("lastDecrementTime=" + lastDecrementTime);
        log.trace("currentTimeMillis=" + currentTimeMillis);
      }
      if (lastDecrementTime > currentTimeMillis) {
        if ((lastDecrementTime - currentTimeMillis) > (5L * 60 * 1000)) {
          // System time changed, reset counter
          if (log.isWarnEnabled()) {
            log.warn("lastDecrementTime>currentTime, system time reset? resetting accounting=" + accounting);
          }
          counter.reset(currentTimeMillis);
        }
      } else {
        long decrementCount = (long) (((currentTimeMillis - lastDecrementTime) * emailLimit.getRate()) / 1000L);
        if (log.isTraceEnabled()) {
          log.trace("lastDecrementTime <= currentTime, decrementCount=" + decrementCount);
        }
        if (decrementCount > 0) {
          counter.decrement(decrementCount, currentTimeMillis);
        }
        if (log.isTraceEnabled()) {
          log.trace("lastDecrementTime <= currentTime, counter.getEmailCount()=" + counter.getEmailCount());
          log.trace("lastDecrementTime <= currentTime, emailLimit.getBurst()=" + emailLimit.getBurst());
        }
      }

      // Check if limit already reached
      if (counter.getEmailCount() < emailLimit.getBurst()) {
        // Increment the counter
        counter.increment();
        // Return not filtered
        return false;
      } else {
        // Enqueue message if should notify
        int notifyDelayMinutes;
        long lastNotifyTime;
        boolean notifyNow = false;
        synchronized (counter) {
          notifyDelayMinutes = counter.getNotifyDelayMinutes();
          lastNotifyTime = counter.getLastNotifyTime();

          if (notifyDelayMinutes == 0) {
            notifyNow = true;
            notifyDelayMinutes = 1;
          } else {
            long timeSince = currentTimeMillis - lastNotifyTime;
            if (timeSince < 0) {
              if (timeSince < (-5L * 60L * 1000L)) {
                // System time reset?
                notifyNow = true;
                notifyDelayMinutes = 1;
              }
            } else {
              if (timeSince >= (notifyDelayMinutes * 60L * 1000L)) {
                notifyNow = true;
                // Increment the notify delay
                switch (notifyDelayMinutes) {
                  case 1:
                    notifyDelayMinutes = 2;
                    break;
                  case 2:
                    notifyDelayMinutes = 5;
                    break;
                  case 5:
                    notifyDelayMinutes = 10;
                    break;
                  case 10:
                    notifyDelayMinutes = 15;
                    break;
                  case 15:
                    notifyDelayMinutes = 30;
                    break;
                  case 30:
                    notifyDelayMinutes = 45;
                    break;
                  case 45:
                    notifyDelayMinutes = 60;
                    break;
                  default:
                    notifyDelayMinutes = 60;
                }
              }
            }
          }
          if (notifyNow) {
            counter.setNotifyDelayMinutes(notifyDelayMinutes);
            counter.setLastNotifyTime(currentTimeMillis);
          }
        }
        if (log.isTraceEnabled()) {
          log.trace("counter=" + counter);
          log.trace("notifyNow=" + notifyNow);
          log.trace("notifyDelayMinutes=" + notifyDelayMinutes);
          log.trace("lastNotifyTime=" + lastNotifyTime);
        }
        if (notifyNow) {
          // Build summary message
          StringBuilder message = new StringBuilder();
          message.append("email ").append(mode.name().toLowerCase(Locale.ENGLISH)).append(" limit reached\n"
              + "    address....: ").append(address).append("\n"
              + "    accounting.: ").append(accounting).append("\n"
              + "    burst......: ").append(emailLimit.getBurst()).append(" emails\n"
              + "    rate.......: ").append(emailLimit.getRate()).append(" emails/second\n"
              + "    next notice: ").append(notifyDelayMinutes).append(notifyDelayMinutes == 1 ? " minute\n" : " minutes\n");
          String messageString = message.toString();
          if (log.isInfoEnabled()) {
            log.info(messageString);
          }

          // Enqueue message
          Notifier.enqueueNotice(
              new Notice(
                  currentTimeMillis,
                  configuration.getSmtpServer(),
                  configuration.getEmailSummaryFrom(),
                  configuration.getEmailSummaryTo(),
                  "email " + mode.name().toLowerCase(Locale.ENGLISH) + " limit reached for " + accounting,
                  messageString
              )
          );
        }

        // Return that it is limited
        if (log.isInfoEnabled()) {
          log.info("email limit exceeded: accounting=" + accounting);
        }
        return !NOTIFY_ONLY_MODE;
      }
    }
  }

  /**
   * TODO: Strip any of the typical spamassassin headers, so that procmail will not default to deliverying if spamassassin fails and doesn't modify the headers
   *       (Prevent spam with spamassassin headers from falling through)
   * TODO: Check for X-Loop here instead of procmail for more performance.
   * TODO: Reenable header in other parts of this code to activate this method.
   */
  @Override
  public JilterStatus header(String headerf, String headerv) {
    if (log.isTraceEnabled()) {
      trace("header:");
      trace("    headerf=\"" + headerf + "\"");
      trace("    headerv=\"" + headerv + "\"");
    }
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("header: returning " + status);
    }
    return status;
  }

  @Override
  public JilterStatus eoh() {
    if (log.isTraceEnabled()) {
      trace("eoh:");
    }
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("eoh: returning " + status);
    }
    return status;
  }

  /**
   * TODO: Call SpamAssassin from here
   * TODO: Limit inbox size here
   * TODO: Reenable body in other parts of this code to activate this method.
   */
  @Override
  public JilterStatus body(ByteBuffer bodyp) {
    if (log.isTraceEnabled()) {
      trace("body:");
    }
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("body: returning " + status);
    }
    return status;
  }

  @Override
  public JilterStatus eom(JilterEOMActions eomActions, Properties properties) {
    if (log.isTraceEnabled()) {
      trace("eom:");
    }
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("eom: returning " + status);
    }
    return status;
  }

  @Override
  public JilterStatus abort() {
    if (log.isTraceEnabled()) {
      trace("abort:");
    }
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("abort: returning " + status);
    }
    return status;
  }

  @Override
  public JilterStatus close() {
    try {
      if (log.isTraceEnabled()) {
        trace("close:");
      }
      init();
    } catch (IOException err) {
      if (log.isErrorEnabled()) {
        log.error("IOException when closing ignored.", err);
      }
    }
    JilterStatus status = JilterStatus.SMFIS_CONTINUE;
    if (log.isTraceEnabled()) {
      trace("close: returning " + status);
    }
    return status;
  }

  @Override
  public int getRequiredModifications() {
    if (log.isTraceEnabled()) {
      trace("getRequiredModifications:");
    }
    if (log.isTraceEnabled()) {
      trace("getRequiredModifications: returning SMFIF_NONE");
    }
    return SMFIF_NONE;
  }

  /*
   * The specific filters follow.
   */

  /**
   * Determines if the current hostaddr is local.
   */
  protected boolean isHostAddrLocal() {
    String hostIp = hostaddr.getHostAddress();
    return configuration.isLocalIpAddress(hostIp);
  }

  /**
   * Checks if relaying has been allowed from hostaddr.
   */
  protected boolean isHostAddrRelayingAllowed() {
    String hostIp = hostaddr.getHostAddress();
    return configuration.isAllowRelay(hostIp);
  }

  /**
   * Makes sure the from address is a valid address on this machine.
   *
   * @return <code>null</code> if passed or <code>JilterStatus</code> for not allowed.
   */
  protected JilterStatus checkFromIsLocal() {
    String parsedFrom = from;

    // Trim the < and > from the from address
    if (
        parsedFrom.length() >= 2
            && parsedFrom.charAt(0) == '<'
            && parsedFrom.charAt(parsedFrom.length() - 1) == '>'
    ) {
      parsedFrom = parsedFrom.substring(1, parsedFrom.length() - 1);
    }

    parsedFrom = stripPlusAddress(parsedFrom);

    // Find the last @ in the address
    int atPos = parsedFrom.lastIndexOf('@');
    if (atPos == -1) {
      return JilterStatus.makeCustomStatus("550", "5.1.7", new String[]{"The from address " + from
          + " must contain both address and domain in the form address@domain, the symbol @ was not found."});
    }

    String domain = parsedFrom.substring(atPos + 1);
    if (domain.length() == 0) {
      return JilterStatus.makeCustomStatus("550", "5.1.8", new String[]{"The from address " + from
          + " must contain both address and domain in the form address@domain, nothing was provided after the @ symbol."});
    }

    String address = parsedFrom.substring(0, atPos);
    if (address.length() == 0) {
      return JilterStatus.makeCustomStatus("550", "5.1.7", new String[]{"The from address " + from
          + " must contain both address and domain in the form address@domain, nothing was provided before the @ symbol."});
    }

    Set<String> addresses = configuration.getAddresses(domain);
    if (addresses == null) {
      return JilterStatus.makeCustomStatus("550", "5.1.8", new String[]{"The from address " + from
          + " is not allowed. This server does not receive email for " + domain});
    }

    if (!addresses.contains(address.toLowerCase(Locale.ENGLISH))) {
      return JilterStatus.makeCustomStatus("550", "5.1.7", new String[]{"The from address " + from
          + " does not exist on this server."});
    }

    return null;
  }

  /**
   * Makes sure the to address is a valid address on this machine.
   *
   * @return <code>null</code> if passed or <code>JilterStatus</code> for not allowed.
   */
  protected JilterStatus checkToIsLocal(String to) {
    String parsedTo = to;

    // Trim the < and > from the to address
    if (
        parsedTo.length() >= 2
            && parsedTo.charAt(0) == '<'
            && parsedTo.charAt(parsedTo.length() - 1) == '>'
    ) {
      parsedTo = parsedTo.substring(1, parsedTo.length() - 1);
    }

    parsedTo = stripPlusAddress(parsedTo);

    // Find the last @ in the address
    int atPos = parsedTo.lastIndexOf('@');
    if (atPos == -1) {
      return JilterStatus.makeCustomStatus("550", "5.1.3", new String[]{"The recipient address " + to
          + " must contain both address and domain in the form address@domain, the symbol @ was not found."});
    }

    String domain = parsedTo.substring(atPos + 1);
    if (domain.length() == 0) {
      return JilterStatus.makeCustomStatus("550", "5.1.2", new String[]{"The recipient address " + to
          + " must contain both address and domain in the form address@domain, nothing was provided after the @ symbol."});
    }

    String address = parsedTo.substring(0, atPos);
    if (address.length() == 0) {
      return JilterStatus.makeCustomStatus("550", "5.1.1", new String[]{"The recipient address " + to
          + " must contain both address and domain in the form address@domain, nothing was provided before the @ symbol."});
    }

    Set<String> addresses = configuration.getAddresses(domain);
    if (addresses == null) {
      return JilterStatus.makeCustomStatus("550", "5.1.2", new String[]{"The recipient address " + to
          + " does not exist on this server. This server does not receive email for " + domain});
    }

    // These addresses are always deliverable
    if (
        "abuse".equalsIgnoreCase(address)
            || "devnull".equalsIgnoreCase(address)
            || "mailer-daemon".equalsIgnoreCase(address)
            || "postmaster".equalsIgnoreCase(address)
    ) {
      return null;
    }

    // Look for an exact match
    boolean found = addresses.contains(address.toLowerCase(Locale.ENGLISH));

    // Also accept wildcards
    if (!found) {
      found = addresses.contains("");
    }

    // If not found, return 5.1.1
    if (!found) {
      return JilterStatus.makeCustomStatus("550", "5.1.1", new String[]{"The recipient address " + to
          + " does not exist on this server."});
    }

    return null;
  }
}

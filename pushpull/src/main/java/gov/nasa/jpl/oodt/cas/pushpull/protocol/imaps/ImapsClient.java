//Copyright (c) 2007, California Institute of Technology.
//ALL RIGHTS RESERVED. U.S. Government sponsorship acknowledged.
//
//$Id$

package gov.nasa.jpl.oodt.cas.pushpull.protocol.imaps;

//JDK imports
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

//Javamail imports
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;

//APACHE imports
import org.apache.commons.codec.DecoderException;

//HTMLParser imports
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.SimpleNodeIterator;

//OODT imports
import gov.nasa.jpl.oodt.cas.pushpull.exceptions.ProtocolException;
import gov.nasa.jpl.oodt.cas.pushpull.protocol.Protocol;
import gov.nasa.jpl.oodt.cas.pushpull.protocol.ProtocolFile;
import gov.nasa.jpl.oodt.cas.pushpull.protocol.ProtocolPath;

/**
 * 
 * @author bfoster
 * @version $Revision$
 * 
 * <p>
 * Describe your class here
 * </p>.
 */
public class ImapsClient extends Protocol {

    static Store store;

    static Folder currentFolder;

    static int port = 993;

    static Session session;

    static int openCalls = 0;

    static int connectCalls = 0;

    // static LinkedList<ProtocolFile> currentFilesForCurrentFolder;
    // static boolean changedDir = true;

    public ImapsClient() {
        super("imaps");
    }

    @Override
    public synchronized void abortCurFileTransfer() throws ProtocolException {
        // do nothing
    }

    @Override
    public synchronized void chDir(ProtocolPath path) throws ProtocolException {
        try {
            String remotePath = path.getPathString();
            // System.out.println("cd to " + remotePath);
            if (remotePath.startsWith("/"))
                remotePath = remotePath.substring(1);
            if (remotePath.trim().equals(""))
                currentFolder = store.getDefaultFolder();
            else {
                currentFolder = store.getFolder(remotePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // changedDir = true;
    }

    @Override
    public synchronized void cdToRoot() throws ProtocolException {
        try {
            chDir(new ProtocolPath("/", true));
        } catch (Exception e) {
            throw new ProtocolException("Failed to cd to root : "
                    + e.getMessage());
        }
    }

    @Override
    public synchronized void connect(String host, String username,
            String password) throws ProtocolException {
        try {
            if (store == null) {
                store = (session = Session.getInstance(System.getProperties()))
                        .getStore("imaps");
                store.connect(host, port, username, password);
                currentFolder = store.getDefaultFolder();
            }
            this.incrementConnections();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ProtocolException("Failed to connected to IMAPS server "
                    + host + " with username " + username + " : "
                    + e.getMessage());
        }
    }

    @Override
    public synchronized void disconnectFromServer() throws ProtocolException {
        decrementConnections();
        if (connectCalls <= 0) {
            // changedDir = true;
            try {
                if (!currentFolder.isOpen()) {
                    try {
                        currentFolder.open(Folder.READ_WRITE);
                    } catch (Exception e) {
                        try {
                            currentFolder.open(Folder.READ_ONLY);
                        } catch (Exception e2) {
                        }
                    }
                }
                currentFolder.close(true);
                store.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                store = null;
            }
        }
    }

    private synchronized void incrementConnections() {
        connectCalls++;
    }

    private synchronized void decrementConnections() {
        if (connectCalls > 0)
            connectCalls--;
    }

    @Override
    public synchronized void getFile(ProtocolFile file, File toLocalFile)
            throws ProtocolException {
        try {
            openFolder(currentFolder);
            Message[] messages = currentFolder.getMessages();
            for (Message message : messages) {
                if (this.getMessageName(message).equals(file.getName())) {
                    writeMessageToLocalFile(message, toLocalFile);
                    // message.setFlag(Flags.Flag.DELETED, true);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ProtocolException("Failed to download " + file + " to "
                    + toLocalFile + " : " + e.getMessage());
        } finally {
            try {
                closeFolder(currentFolder);
            } catch (Exception e) {
            }
        }
    }

    private void writeMessageToLocalFile(Message message, File toLocalFile)
            throws MessagingException, ParserException, IOException,
            DecoderException {
        PrintStream ps = new PrintStream(new FileOutputStream(toLocalFile));

        ps.print("From:");
        Address[] senders = message.getFrom();
        for (Address address : senders)
            ps.print(" " + address.toString());

        ps.print("\nTo:");
        Address[] recipients = message.getAllRecipients();
        for (Address address : recipients)
            ps.print(" " + address.toString());

        ps.println("\nSubject: " + message.getSubject());

        ps.println("----- ~ Message ~ -----");
        String content = this.getContentFromHTML(message);
        if (content.equals(""))
            content = this.getContentFromPlainText(message);
        ps.println(content);

        ps.close();
    }

    @Override
    public synchronized boolean isConnected() throws ProtocolException {
        return store.isConnected();
    }

    @Override
    public List<ProtocolFile> listFiles() throws ProtocolException {
        // if (changedDir) {
        // System.out.println("Refreshed LS");
        // currentFilesForCurrentFolder = new LinkedList<ProtocolFile>();
        LinkedList<ProtocolFile> currentFilesForCurrentFolder = new LinkedList<ProtocolFile>();
        try {
            openFolder(currentFolder);
            if (!currentFolder.getFullName().equals(
                    store.getDefaultFolder().getFullName())) {
                Message[] messages = currentFolder.getMessages();
                for (Message message : messages) {
                    currentFilesForCurrentFolder.add(new ProtocolFile(this
                            .getRemoteSite(), new ProtocolPath(this.pwd()
                            .getProtocolPath().getPathString()
                            + "/" + this.getMessageName(message), false)));
                }
            }
            // changedDir = false;
        } catch (Exception e) {
            if (!currentFolder.getFullName().equals(""))
                throw new ProtocolException("Failed to ls");
        } finally {
            try {
                closeFolder(currentFolder);
            } catch (Exception e) {
            }
        }
        // }
        return currentFilesForCurrentFolder;
    }

    @Override
    public synchronized ProtocolFile getCurrentWorkingDir()
            throws ProtocolException {
        try {
            String pwd = this.currentFolder.getFullName();
            if (!pwd.equals("") && !pwd.startsWith("/"))
                pwd = "/" + pwd;
            return new ProtocolFile(this.getRemoteSite(), new ProtocolPath(pwd,
                    true));
        } catch (Exception e) {
            throw new ProtocolException("Failed to pwd : " + e.getMessage());
        }
    }

    private String getMessageName(Message msg) throws MessagingException {
        Enumeration headers = msg.getAllHeaders();
        while (headers.hasMoreElements()) {
            Header header = (Header) headers.nextElement();
            if (header.getName().toLowerCase().equals("message-id")) {
                String stringHeader = header.getValue();
                // System.out.println(stringHeader);
                stringHeader = stringHeader.replace("<", "");
                return stringHeader.substring(0, stringHeader.indexOf("@"));
            }
        }
        return null;
    }

    private String getContentFromPlainText(Part p) throws MessagingException,
            IOException, ParserException, DecoderException {
        StringBuffer content = new StringBuffer("");
        if (p.isMimeType("text/plain")) {
            content.append((String) p.getContent());
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++)
                content.append(getContentFromPlainText(mp.getBodyPart(i)));
        } else {
            Object obj = p.getContent();
            if (obj instanceof Part)
                content.append(getContentFromPlainText((Part) p.getContent()));
        }
        return content.toString().replaceAll(" \\r\\n", "").replaceAll(" \\n",
                "");
    }

    private String getContentFromHTML(Part p) throws MessagingException,
            IOException, ParserException, DecoderException {
        StringBuffer content = new StringBuffer("");
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++)
                content.append(getContentFromHTML(mp.getBodyPart(i)));
        } else if (p.isMimeType("text/html")) {
            Parser parser = Parser.createParser((String) p.getContent(), null);
            NodeList nl = parser.parse(new NodeFilter() {
                public boolean accept(Node arg0) {
                    return arg0 instanceof TextNode;
                }
            });
            SimpleNodeIterator sni = nl.elements();
            while (sni.hasMoreNodes()) {
                content.append(sni.nextNode().getText() + "\n");
            }
        } else {
            Object obj = p.getContent();
            if (obj instanceof Part)
                content.append(getContentFromHTML((Part) p.getContent()));
        }
        return content.toString();
    }

    private synchronized void openFolder(Folder folder)
            throws ProtocolException {
        if (!folder.isOpen()) {
            try {
                folder.open(Folder.READ_WRITE);
            } catch (Exception e) {
                try {
                    folder.open(Folder.READ_ONLY);
                } catch (Exception e2) {
                    throw new ProtocolException("Failed to open folder : "
                            + e.getMessage() + " : " + e2.getMessage());
                }
            }
        }
        openCalls++;
    }

    private synchronized void closeFolder(Folder folder) {
        if (openCalls > 0)
            openCalls--;

        if (openCalls <= 0) {
            try {
                folder.close(true);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public synchronized boolean deleteFile(ProtocolFile file) {
        boolean found = false;
        try {
            openFolder(currentFolder);
            Message[] messages = currentFolder.getMessages();
            for (Message message : messages) {
                if (this.getMessageName(message).equals(file.getName())) {
                    message.setFlag(Flags.Flag.DELETED, true);
                    found = true;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeFolder(currentFolder);
        }
        return found;
    }

}
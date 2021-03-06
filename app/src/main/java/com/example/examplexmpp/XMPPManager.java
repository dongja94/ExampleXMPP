package com.example.examplexmpp;

import android.os.Handler;
import android.os.Looper;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.iqregister.AccountManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class XMPPManager {
    private static XMPPManager instance;

    public static XMPPManager getInstance() {
        if (instance == null) {
            instance = new XMPPManager();
        }
        return instance;
    }

    private final static String DOMAIN = "dongja94.gonetis.com";
    private final static String HOST = "dongja94.gonetis.com";

    //	private final static String DOMAIN = "talk.google.com";
//    private final static String SERVICE = "gmail.com";
    private final static int PORT = 5222;
    private XMPPTCPConnection mXmppConnection;
    private FileTransferManager mFileManager;
    Handler mHandler = new Handler(Looper.getMainLooper());
    ThreadPoolExecutor mExecutor;

    Map<String, Chat> mChatMap = new HashMap<String, Chat>();

    private XMPPManager() {
        XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder();
        configBuilder.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        configBuilder.setResource("Android");
        configBuilder.setServiceName(DOMAIN);
        configBuilder.setHost(HOST);
        configBuilder.setPort(PORT);
        mXmppConnection = new XMPPTCPConnection(configBuilder.build());
        mXmppConnection.addConnectionListener(connectionListener);
        mExecutor = new ThreadPoolExecutor(3, 10, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
    }

    ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void connected(XMPPConnection connection) {

        }

        @Override
        public void authenticated(XMPPConnection connection, boolean resumed) {

        }

        @Override
        public void connectionClosed() {

        }

        @Override
        public void connectionClosedOnError(Exception e) {

        }

        @Override
        public void reconnectionSuccessful() {

        }

        @Override
        public void reconnectingIn(int seconds) {

        }

        @Override
        public void reconnectionFailed(Exception e) {

        }
    };

    public interface OnActionListener<T> {
        public void onSuccess(T... data);

        public void onFail(int code);
    }

    public static final int ERROR_CODE = -1;

    private <T> void sendAction(final OnActionListener<T> listener, final boolean isSuccess, final T... data) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    if (isSuccess) {
                        listener.onSuccess(data);
                    } else {
                        listener.onFail(ERROR_CODE);
                    }
                }
            }
        });
    }

    public void connectServer(final OnActionListener<Void> listener) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                boolean isConnected = connect();
                sendAction(listener, isConnected);
            }
        });
    }

    private boolean connect() {
        if (mXmppConnection.isConnected()) {
            return true;
        }
        try {
            mXmppConnection.connect();
            return true;
        } catch (SmackException | IOException | XMPPException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void login(final String userId, final String password, final OnActionListener<Void> listener) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                boolean loginSuccess = login(userId, password);
                sendAction(listener, loginSuccess);
            }
        });
    }

    public boolean isLogin() {
        return mXmppConnection.isAuthenticated();
    }

    private boolean login(String userId, String password) {
        if (!connect()) {
            return false;
        }
        try {
            mXmppConnection.login(userId, password);
            initializeAfterLogin();
            return true;
        } catch (XMPPException | IOException | SmackException e) {
            e.printStackTrace();
        }
        return false;
    }

    public interface OnMessageListener {
        public void onReceived(Chat chat, Message message);
    }

    private List<OnMessageListener> mMessageListenerList = new ArrayList<OnMessageListener>();

    public void registerMessageListener(OnMessageListener listener) {
        if (!mMessageListenerList.contains(listener)) {
            mMessageListenerList.add(listener);
        }
    }

    public void unregisterMessageListener(OnMessageListener listener) {
        mMessageListenerList.remove(listener);
    }

    public void broadcastMessage(Chat chat, Message message) {
        for (OnMessageListener listener : mMessageListenerList) {
            listener.onReceived(chat, message);
        }
    }

    ChatMessageListener mMessageListener = new ChatMessageListener() {

        @Override
        public void processMessage(Chat chat, Message message) {
            broadcastMessage(chat, message);
        }
    };

    ChatManagerListener mChatManagerListener = new ChatManagerListener() {

        @Override
        public void chatCreated(Chat chat, boolean createdLocally) {
            String userid = getUserId(chat.getParticipant());

            Chat old = mChatMap.get(userid);
            if (old != null) {
                if (!old.equals(chat)) {
//					old.close();
                }
                mChatMap.remove(userid);
            }
            mChatMap.put(userid, chat);
            chat.addMessageListener(mMessageListener);
        }
    };

    public String getUserId(String participant) {
        String[] s = participant.split("/");
        return s[0].trim();
    }

    public interface OnFileReceiveListener {
        public void onReceivedFile(FileTransferRequest request);
    }

    private List<OnFileReceiveListener> mFileReceiverList = new ArrayList<OnFileReceiveListener>();

    public void registerOnFileReceiveListener(OnFileReceiveListener listener) {
        if (!mFileReceiverList.contains(listener)) {
            mFileReceiverList.add(listener);
        }
    }

    public void unregisterOnFileReceiveListener(OnFileReceiveListener listener) {
        mFileReceiverList.remove(listener);
    }

    private void broadcastFileRequest(FileTransferRequest request) {
        for (OnFileReceiveListener listener : mFileReceiverList) {
            listener.onReceivedFile(request);
        }
    }

    private void initializeAfterLogin() throws IOException {
        if (!mXmppConnection.isAuthenticated()) {
            throw new IOException("Not Login");
        }
//		ChatManager chatManager = ChatManager.getInstanceFor(mXmppConnection);
        ChatManager chatManager = ChatManager.getInstanceFor(mXmppConnection);
        chatManager.addChatListener(mChatManagerListener);

//		mFileManager = new FileTransferManager(mXmppConnection);
//		mFileManager.addFileTransferListener(new FileTransferListener() {
//			
//			@Override
//			public void fileTransferRequest(FileTransferRequest request) {
//				broadcastFileRequest(request);
//			}
//		});
    }

    public void setPresence(final String status, final OnActionListener<Void> listener) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                boolean isSuccess = setPresence(status);
                sendAction(listener, isSuccess);
            }
        });
    }

    private boolean setPresence(String status) {
        if (mXmppConnection.isAuthenticated()) {
            return false;
        }
        Presence presence = new Presence(Presence.Type.available);
        presence.setStatus(status);
        try {
            mXmppConnection.sendStanza(presence);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean addAccount(String userid, String password, Map<String, String> attributes) {
        if (!connect()) {
            return false;
        }
        AccountManager accountManager = AccountManager.getInstance(mXmppConnection);
        try {
            if (accountManager.supportsAccountCreation()) {
                accountManager.createAccount(userid, password, attributes);
                return true;
            }
        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        } catch (XMPPException.XMPPErrorException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void getRoster(final OnActionListener<User> listener) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                List<User> list = getRoster();
                boolean isSuccess = (list == null) ? false : true;
                User[] users = list.toArray(new User[list.size()]);
                sendAction(listener, isSuccess, users);
            }
        });
    }

    private List<User> getRoster() {
        List<User> list = new ArrayList<User>();
        if (!mXmppConnection.isAuthenticated()) {
            return null;
        }
        Roster roster = Roster.getInstanceFor(mXmppConnection);
        Collection<RosterEntry> entries = roster.getEntries();
        for (RosterEntry entry : entries) {
            User user = new User();
            user.entry = entry;
            user.presence = roster.getPresence(entry.getUser());
            list.add(user);
        }
        return list;
    }

    public void addRosterListener() {
        Roster roster = Roster.getInstanceFor(mXmppConnection);
        roster.addRosterListener(new RosterListener() {

            @Override
            public void presenceChanged(Presence presence) {

            }

            @Override
            public void entriesUpdated(Collection<String> addresses) {

            }

            @Override
            public void entriesDeleted(Collection<String> addresses) {

            }

            @Override
            public void entriesAdded(Collection<String> addresses) {

            }
        });
    }

    private RosterEntry addRosterEntry(String user, String name, String... groups) {
        if (!mXmppConnection.isAuthenticated()) {
            return null;
        }
        Roster roster = Roster.getInstanceFor(mXmppConnection);
        if (!roster.contains(user)) {
            for (String group : groups) {
                RosterGroup rg = roster.getGroup(group);
                if (rg == null) {
                    roster.createGroup(group);
                }
            }
            try {
                roster.createEntry(user, name, groups);
            } catch (SmackException.NotLoggedInException e) {
                e.printStackTrace();
            } catch (SmackException.NoResponseException e) {
                e.printStackTrace();
            } catch (XMPPException.XMPPErrorException e) {
                e.printStackTrace();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
            return roster.getEntry(user);
        }
        return null;
    }


    public Chat getChat(String userId) {
        Chat chat = mChatMap.get(userId);
        if (chat == null) {
//			ChatManager chatManager = ChatManager.getInstanceFor(mXmppConnection);
            ChatManager chatManager = ChatManager.getInstanceFor(mXmppConnection);
            chat = chatManager.createChat(userId, null);
        }
        return chat;
    }

    public Chat createChat(String userId) {
        ChatManager chatManager = ChatManager.getInstanceFor(mXmppConnection);
        Chat chat = chatManager.createChat(userId, new ChatMessageListener() {

            @Override
            public void processMessage(Chat chat, Message message) {
                broadcastMessage(chat, message);
            }
        });
        return chat;
    }

    public void sendChatMessage(final Chat chat, final String message, final OnActionListener<Void> listener) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                boolean isSuccess = sendChatMessage(chat, message);
                sendAction(listener, isSuccess);
            }
        });
    }

    private boolean sendChatMessage(Chat chat, String message) {
        try {
            chat.sendMessage(message);
            return true;
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static final int TRANSFER_COMPLETED = -1;
    public static final int TRANSFER_NEGOTIATING = -2;
    public static final int TRANSFER_NEGOTIATED = -3;
    public static final int TRANSFER_REFUSED = -4;
    public static final int TRANSFER_CANCELLED = -5;

    public void sendFile(final Chat chat, final File file, final OnActionListener<Integer> listener) {
        mExecutor.execute(new Runnable() {

            @Override
            public void run() {
                OutgoingFileTransfer outfile = mFileManager.createOutgoingFileTransfer(chat.getParticipant());
                try {
                    outfile.sendFile(file, "file transfer...");
                    while (true) {
                        Status status = outfile.getStatus();
                        if (status == Status.in_progress) {
                            int progress = (int) (outfile.getProgress() * 100);
                            sendAction(listener, true, progress);
                        }
                        if (status == Status.complete) {
                            sendAction(listener, true, TRANSFER_COMPLETED);
                            break;
                        }
                        if (status == Status.error) {
                            sendAction(listener, false);
                            break;
                        }
                        if (status == Status.cancelled) {
                            sendAction(listener, true, TRANSFER_CANCELLED);
                            break;
                        }
                        if (status == Status.refused) {
                            sendAction(listener, true, TRANSFER_REFUSED);
                            break;
                        }
                        if (status == Status.negotiating_transfer || status == Status.negotiating_stream) {
                            sendAction(listener, true, TRANSFER_NEGOTIATING);
                        }
                        if (status == Status.negotiated) {
                            sendAction(listener, true, TRANSFER_NEGOTIATED);
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }
}

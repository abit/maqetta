package org.davinci.server.internal.user;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.davinci.server.IDavinciServerConstants;
import org.davinci.server.ServerManager;
import org.davinci.server.VResourceUtils;
import org.davinci.server.internal.Activator;
import org.davinci.server.user.Person;
import org.davinci.server.user.PersonManager;
import org.davinci.server.user.User;
import org.davinci.server.user.UserException;
import org.davinci.server.user.UserManager;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.osgi.framework.Bundle;

public class UserManagerImpl implements UserManager {

    static UserManagerImpl theUserManager;
    HashMap                users    = new HashMap();
    public File            baseDirectory;

    PersonManager          personManager;
    int                    maxUsers = 0;
    private int            usersCount;

    public UserManagerImpl() {
        ServerManager serverManger = ServerManager.getServerManger();
        String basePath = serverManger.getDavinciProperty(IDavinciServerConstants.BASE_DIRECTORY_PROPERTY);
        File userDir = null;
        if (basePath != null && basePath.length() > 0) {
            File dir = new File(basePath);
            if (dir.exists()) {
                userDir = dir;
            } else {
                System.out.println("dir doesnt exist");
            }
        }
        if (userDir == null) {
            userDir = (File) serverManger.servletConfig.getServletContext().getAttribute("javax.servlet.context.tempdir");
        }
        if (userDir == null) {
            userDir = new File(".");
        }
        this.baseDirectory = userDir;

        this.usersCount = userDir.list().length;
        if (ServerManager.DEBUG_IO_TO_CONSOLE) {
            System.out.println("\nSetting [user space] to: " + baseDirectory.getAbsolutePath());
        }
        System.out.println("\nSetting [user space] to: " + baseDirectory.getAbsolutePath());

        String maxUsersStr = serverManger.getDavinciProperty(IDavinciServerConstants.MAX_USERS);
        if (maxUsersStr != null && maxUsersStr.length() > 0) {
            this.maxUsers = Integer.valueOf(maxUsersStr).intValue();
        }

        this.createPersonManager();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.davinci.server.user.impl.UserManager#hasPermisions(org.davinci.server
     * .user.User, org.davinci.server.user.User, java.lang.String)
     */
    public boolean hasPermisions(User owner, User requester, String resource) {
        /*
         * deny permision to direct access of a users workspace
         */
        return (resource != "");
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.davinci.server.user.impl.UserManager#getUser(java.lang.String)
     */
    public User getUser(String userName) {

        User user = (User) users.get(userName);
        if (user == null && ServerManager.LOCAL_INSTALL && IDavinciServerConstants.LOCAL_INSTALL_USER.equals(userName)) {
            return this.getSingleUser();
        }
        if (user == null && this.checkUserExists(userName)) {
            Person person = this.personManager.getPerson(userName);
            user = new User(person, new File(this.baseDirectory, userName));
        }
        return user;

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.davinci.server.user.impl.UserManager#addUser(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    public User addUser(String userName, String password, String email) throws UserException {

        if (checkUserExists(userName)) {
            throw new UserException(UserException.ALREADY_EXISTS);
        }

        if (this.maxUsers > 0 && this.usersCount >= this.maxUsers) {
            throw new UserException(UserException.MAX_USERS);
        }
        Person person = this.personManager.addPerson(userName, password, email);
        if (person != null) {

            User user = new User(person, new File(this.baseDirectory, userName));
            users.put(userName, user);
            user.createProject(IDavinciServerConstants.DEFAULT_PROJECT);
            this.usersCount++;
            return user;
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.davinci.server.user.impl.UserManager#removeUser(java.lang.String)
     */
    public void removeUser(String userName) throws UserException {

        if (!checkUserExists(userName)) {
            return;
        }
        /*
         * would call this.personManager.removePerson(userName) here
         */
        File userDir = new File(this.baseDirectory, userName);
        VResourceUtils.deleteDir(userDir);
        users.remove(userName);
        this.usersCount--;
    }

   

    /*
     * (non-Javadoc)
     * 
     * @see org.davinci.server.user.impl.UserManager#login(java.lang.String,
     * java.lang.String)
     */
    public User login(String userName, String password) {
        if (!checkUserExists(userName)) {
            return null;
        }
        Person person = this.personManager.login(userName, password);
        if (person != null) {
            return new User(person, new File(this.baseDirectory, userName));
        }
        return null;
    }

    private boolean checkUserExists(String userName) {
        File userDir = new File(this.baseDirectory, userName);
        return userDir.exists();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.davinci.server.user.impl.UserManager#isValidUser(java.lang.String)
     */
    public boolean isValidUser(String userName) {
        if (ServerManager.LOCAL_INSTALL && IDavinciServerConstants.LOCAL_INSTALL_USER.equals(userName)) {
            return true;
        }
        User user = getUser(userName);
        return user != null;
    }

    public User getSingleUser() {
        class LocalPerson implements Person {
            public String getEmail() {
                return "";
            }

            public String getUserName() {
                return IDavinciServerConstants.LOCAL_INSTALL_USER;
            }
        }
        
        
        File userDir = this.baseDirectory;
        /*
        userDir.mkdir();
        File settingsDir = new File(userDir, IDavinciServerConstants.SETTINGS_DIRECTORY_NAME);
        if (!settingsDir.exists()) {
            settingsDir.mkdir();
            initNewProject(userDir);
        }
        */
        User user = new User(new LocalPerson(), userDir);
        user.createProject(IDavinciServerConstants.DEFAULT_PROJECT);
        return user;
    }

    public PersonManager getPersonManager() {
        return this.personManager;
    }

    private void createPersonManager() {
        IConfigurationElement libraryElement = ServerManager.getServerManger().getExtension(IDavinciServerConstants.EXTENSION_POINT_PERSON_MANAGER,
                IDavinciServerConstants.EP_TAG_PERSON_MANAGER);
        if (libraryElement != null) {
            try {
                this.personManager = (PersonManager) libraryElement.createExecutableExtension(IDavinciServerConstants.EP_ATTR_PERSON_MANAGER_CLASS);
            } catch (CoreException e) {
                e.printStackTrace();
            }
        }
        if (this.personManager == null) {
            this.personManager = new PersonManagerImpl(this.baseDirectory);
        }

    }

}

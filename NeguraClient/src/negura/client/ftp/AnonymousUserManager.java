package negura.client.ftp;

import java.util.Arrays;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.AbstractUserManager;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;

public class AnonymousUserManager extends AbstractUserManager {
    private BaseUser anonUser;

    public AnonymousUserManager(int concurrentUsers) {
        super("admin", new ClearTextPasswordEncryptor());

        anonUser = new BaseUser();
        anonUser.setName("anonymous");
        anonUser.setAuthorities(Arrays.asList(new Authority[]{
            new ConcurrentLoginPermission(concurrentUsers, concurrentUsers)}));
        anonUser.setEnabled(true);
        anonUser.setHomeDirectory("/");
        anonUser.setMaxIdleTime(10000);
    }

    @Override
    public User getUserByName(String username) throws FtpException {
        if (anonUser.getName().equals(username))
            return anonUser;
        return null;
    }

    @Override
    public String[] getAllUserNames() throws FtpException {
        return new String[]{anonUser.getName()};
    }

    @Override
    public void delete(String username) throws FtpException { }

    @Override
    public void save(User user) throws FtpException { }

    @Override
    public boolean doesExist(String username) throws FtpException {
        return anonUser.getName().equals(username);
    }

    @Override
    public User authenticate(Authentication authentication)
            throws AuthenticationFailedException {
        if (AnonymousAuthentication.class.isAssignableFrom(
                authentication.getClass()))
            return anonUser;
        return null;
    }
}
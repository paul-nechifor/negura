package negura.client.ftp;

import negura.client.fs.NeguraFile;
import negura.client.fs.NeguraFsView;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 * File system view based on Negura file system for the use of the FTP server.
 * @author Paul Nechifor
 */
public final class NeguraFtpFsView implements FileSystemView {
    private NeguraFsView fsView;
    private String workingDir = "/";

    public NeguraFtpFsView(NeguraFsView fsView) {
        this.fsView = fsView;
    }

    private String absolutePath(String path) {
        if (path.charAt(0) == '/')
            return path;
        if (workingDir.equals("/"))
            return workingDir + path;
        return workingDir + "/" + path;
    }

    public boolean changeWorkingDirectory(String path) throws FtpException {
        String absolute = absolutePath(path);
        if (fsView.getFile(absolute) == null)
            return false;
        workingDir = absolute;
        return true;
    }
    
    public void dispose() { }

    public FtpFile getFile(String path) throws FtpException {
        NeguraFile f = fsView.getFile(absolutePath(path));
        if (f == null)
            throw new FtpException("No such file.");
        return new NeguraFtpFile(f);
    }

    public FtpFile getHomeDirectory() throws FtpException {
        return getFile("/");
    }

    public FtpFile getWorkingDirectory() throws FtpException {
        return getFile(workingDir);
    }

    public boolean isRandomAccessible() throws FtpException {
        return true;
    }
}

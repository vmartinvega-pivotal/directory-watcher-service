package directory.watcher.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by Adrian Perez on 2/11/16.
 */
@Component
@ConfigurationProperties(prefix = "directoryService")
public class DirectoryServiceProperties {
    private String directory;
    private String symlinkDirectory;
    private int secondsWithoutModifications;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getSymlinkDirectory() {
        return symlinkDirectory;
    }

    public void setSymlinkDirectory(String directory) {
        this.symlinkDirectory = directory;
    }

    public int getSecondsWithoutModifications() {
        return secondsWithoutModifications;
    }

    public void setSecondsWithoutModifications(int seconds) {
        this.secondsWithoutModifications = seconds;
    }
}

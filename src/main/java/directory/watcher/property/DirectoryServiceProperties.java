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
    private int minutesWithoutModifications;
    private int minutesToPurgeAndClean;

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

    public int getMinutesWithoutModifications() {
        return minutesWithoutModifications;
    }

    public void setMinutesWithoutModifications(int minutes) {
        this.minutesWithoutModifications = minutes;
    }

    public int getMinutesToPurgeAndClean() {
        return minutesToPurgeAndClean;
    }

    public void setMinutesToPurgeAndClean(int minutes) {
        this.minutesToPurgeAndClean = minutes;
    }
}

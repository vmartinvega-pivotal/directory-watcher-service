package directory.watcher.service;

import java.util.Map;
import java.util.HashMap;
import directory.watcher.property.DirectoryServiceProperties;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import static java.nio.file.StandardWatchEventKinds.*;
import java.time.Instant;

@Service
public class DirectoryWatcherService {

    private WatchService watcher;
    private Map<WatchKey, Path> keys;

    private DirectoryServiceProperties properties;
    private static final Logger LOGGER = Logger.getLogger(DirectoryWatcherService.class);

    private Instant lastCleanDone = null;

    @Autowired
    public DirectoryWatcherService(DirectoryServiceProperties properties) {
        this.properties = properties;
        try {
            this.watcher = FileSystems.getDefault().newWatchService();
            this.keys = new HashMap<WatchKey, Path>();
            lastCleanDone = Instant.now();
            LOGGER.info("Constructor: " + properties);
            Path dir = Paths.get(properties.getDirectory());
            Path symLinkDir = Paths.get(properties.getSymlinkDirectory());
            initDirectory(symLinkDir);
            initDirectory(dir);
            cleanSymbolicLinks(symLinkDir);
            purgeSymbolicLinks(symLinkDir);
            walkFileTree(dir);
            walkAndRegisterDirectories(dir);
        } catch (IOException ea) {
            LOGGER.error(ea);
        }
    }

    /**
     *  Purge all simbolic links for files that are more than the specified time without modifications (the pipeline just finished)
     */
    private void purgeSymbolicLinks(Path dir) throws IOException {
         DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(properties.getSymlinkDirectory()));
        for (Path path : stream) {
            if (Files.isSymbolicLink(path)) {
                Path targetSymlink = Files.readSymbolicLink(path);
                long now = Instant.now().getEpochSecond();
                BasicFileAttributes attr = Files.readAttributes(targetSymlink, BasicFileAttributes.class);
                long lastModified = attr.lastModifiedTime().toInstant().getEpochSecond();
                lastModified = lastModified + ( properties.getMinutesWithoutModifications() * 60 );

                if (lastModified < now){
                    LOGGER.info("Deleted symbolicLink: " + path + ". There were more than " + properties.getMinutesWithoutModifications() + " minutes without modifications in the target file: " + targetSymlink);
                    Files.delete(path);
                }
            }
        }
    }
 
    /**
     * Register the given directory with the WatchService; This function will be called by FileVisitor
     */
    private void registerDirectory(Path dir) throws IOException {
        //WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        WatchKey key = dir.register(watcher, ENTRY_CREATE);
        keys.put(key, dir);
    }

    /**
     * Inits the output directory where will be placed all symbolic links. 
     */
    private void initDirectory(Path dir) throws IOException{
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } 
    }
 
    /**
     * Register the given directory, and all its sub-directories, with the WatchService.
     */
    private void walkAndRegisterDirectories(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
      *  Delete orphan symbolic links
     */
    private void cleanSymbolicLinks(Path dir) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        for (Path path : stream) {
            if (Files.isSymbolicLink(path)) {
                Path targetSymlink = Files.readSymbolicLink(path);
                if (!Files.exists(targetSymlink)) {
                    LOGGER.info("Deleted symbolicLink: " + path + ". The target " + targetSymlink + " does not exist ");
                    Files.delete(path);
                }
            }
        }
    }

    public synchronized void cleanOutputDir() throws IOException{
        Instant now = Instant.now();
        if ( ( now.getEpochSecond() - (properties.getMinutesToPurgeAndClean() * 60) ) > lastCleanDone.getEpochSecond() ){
            LOGGER.info("Cleaning simbolic links on: " + now + ", Last time was done on: " + lastCleanDone );
            Path outDir = Paths.get(properties.getSymlinkDirectory());
            cleanSymbolicLinks(outDir);
            purgeSymbolicLinks(outDir);
            lastCleanDone = now;
        }
    }
 
    /**
     * Process all events for keys queued to the watcher
     */
    public void processEvents() {
        for (;;) {
 
            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }
 
            Path dir = keys.get(key);
            if (dir == null) {
                LOGGER.error("WatchKey not recognized!!");
                continue;
            }
 
            for (WatchEvent<?> event : key.pollEvents()) {
                @SuppressWarnings("rawtypes")
                WatchEvent.Kind kind = event.kind();
 
                // Context for directory entry event is the file name of entry
                @SuppressWarnings("unchecked")
                Path name = ((WatchEvent<Path>)event).context();
                Path child = dir.resolve(name);
 
                // print out event
                LOGGER.debug(event.kind().name() + ": " + child);
 
                try {
                    // if directory is created, and watching recursively, then register it and its sub-directories
                    if (kind == ENTRY_CREATE) {
                        if (Files.isDirectory(child)) {
                            walkAndRegisterDirectories(child);
                        } else{
                            // Checks if the file is a log file (jenkins job log)
                            if (Files.isRegularFile(child) && child.endsWith("log")){
                                createSymbolicLink (child);
                            }
                        }
                    }

                    // Clean output directory
                    cleanOutputDir();
                } catch (IOException x) {
                    // do something useful
                }
            }
 
            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
 
                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    public DirectoryServiceProperties getProperties() {
        return properties;
    }

    public void setProperties(DirectoryServiceProperties properties) {
        this.properties = properties;
    }

    private void createSymbolicLink(Path target) throws IOException {
        UUID uuid = UUID.randomUUID();
        Path link = Paths.get(properties.getSymlinkDirectory(), uuid.toString());
        LOGGER.info("Creating simbolic link: " + link + " with target: " + target + " to be monitored.");
        Files.createSymbolicLink(link, target);
    }

    /**
     * Gets the last modification in a file that is being monitored
     */
    private Instant getLastModification(Path dir) throws IOException {
        Instant result = null;
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        for (Path path : stream) {
            if (Files.isSymbolicLink(path)) {
                Path targetSymlink = Files.readSymbolicLink(path);
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                if (    (result == null) || 
                        (attr.lastModifiedTime().toInstant().getEpochSecond() > result.getEpochSecond()) ){
                    result = attr.lastModifiedTime().toInstant();
                }
            }
        }   
        return result;
    }

    /**
     *  This function walks the input directory looking for log files to be added for fluentd, these files were created while the pod
     *  was stopped, that is, from the last modification added. 
     */
    private void walkFileTree(Path dir) throws IOException {
        Path symLinkDir = Paths.get(properties.getSymlinkDirectory());
        final Instant lastModification = getLastModification(symLinkDir);
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {

                // Is a log file (jenkins job log)
                if (Files.isRegularFile(path) && path.endsWith("log")){
                    BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                    if  ( (lastModification != null) && (attr.lastModifiedTime().toInstant().getEpochSecond() > lastModification.getEpochSecond()) ){
                        createSymbolicLink (path);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }
}

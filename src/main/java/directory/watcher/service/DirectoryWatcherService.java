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

    @Autowired
    public DirectoryWatcherService(DirectoryServiceProperties properties) {
        this.properties = properties;
        try {
            this.watcher = FileSystems.getDefault().newWatchService();
            this.keys = new HashMap<WatchKey, Path>();
            initOutputSymlinkDirectory(Paths.get(properties.getSymlinkDirectory()));
            cleanSymbolicLinks(Paths.get(properties.getSymlinkDirectory()));
            walkAndRegisterDirectories(Paths.get(properties.getDirectory()));
        } catch (IOException ea) {
            LOGGER.error(ea);
        }
    }

    /**
     * Removes a symbolicLink in the output directory with target is target
     */
    private void deleteSymboliclinkWithTarget(Path target) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(properties.getSymlinkDirectory()));
        for (Path path : stream) {
            if (Files.isSymbolicLink(path)) {
                if (Files.isDirectory(target)) {
                    if (path.getRoot().toString().startsWith(target.toString())){
                        Files.delete(path);
                    }
                }else{
                    Path targetSymlink = Files.readSymbolicLink(path);
                    if (targetSymlink.equals(target)){
                        Files.delete(path);
                        System.out.println("Deleted symbolicLink: " + path);
                        break;
                    }
                }
            }
        }
    }

    private void purgeSymbolicLinks(Path dir) throws IOException {
         DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(properties.getSymlinkDirectory()));
        for (Path path : stream) {
            if (Files.isSymbolicLink(path)) {
                long now = Instant.now().getEpochSecond();
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                long lastModified = attr.lastModifiedTime().toInstant().getEpochSecond();
                lastModified = lastModified + properties.getSecondsWithoutModifications();

                if (lastModified < now){
                    System.out.println("Deleted symbolicLink: " + path + ". More than " + properties.getSecondsWithoutModifications() + " without modifications");
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
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
        keys.put(key, dir);
    }

    /**
     * Inits the output directory where will be placed all symbolic links. 
     */
    private void initOutputSymlinkDirectory(Path dir) throws IOException{
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
                    Files.delete(path);
                }
            }
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
                System.err.println("WatchKey not recognized!!");
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
                System.out.format("%s: %s\n", event.kind().name(), child);
 
                try {
                    // if directory is created, and watching recursively, then register it and its sub-directories
                    if (kind == ENTRY_CREATE) {
                        if (Files.isDirectory(child)) {
                            walkAndRegisterDirectories(child);
                        } else{
                            UUID uuid = UUID.randomUUID();
                            Path link = Paths.get(properties.getSymlinkDirectory(), uuid.toString());
                            createSymbolicLink (child, link);
                        }
                    }else if (kind == ENTRY_DELETE) {
                        deleteSymboliclinkWithTarget(child);
                    }
                    purgeSymbolicLinks(Paths.get(properties.getSymlinkDirectory()));
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

    private void createSymbolicLink(Path target, Path link) throws IOException {
        if (Files.exists(link)) {
            Files.delete(link);
        }
        Files.createSymbolicLink(link, target);
    }
}

package org.red5.ndi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NDI interface to the library.
 *
 * @author Paul Gregoire
 */
public class NDI {

    private static Logger log = LoggerFactory.getLogger(NDI.class);

    // debugging use
    @SuppressWarnings("unused")
    private static boolean debug;

    // use System.load to force loading of libraries before this library itself
    private static boolean forceLoad = true;

    // libraries being force-loaded if flag is set to true
    private static List<String> forcedLoadPaths;

    // static atomic loaded flag
    private static final AtomicBoolean loaded = new AtomicBoolean(false);

    // collect the arch and name just once and reuse x times
    private static final String ao = System.getProperty("os.arch") + "-" + System.getProperty("os.name").replaceAll(" ", "");

    // switched to utilize a single string for the OS and compiler
    private static String osDescriptor = getAOL();

    // library name for our primary shared lib
    private static String libraryName;

    /**
     * Load jni library
     */
    public static void loadLibrary() {
        log.info("NDI on {}", osDescriptor);
        if (!loaded.get()) {
            String javaLibraryPath = System.getProperty("java.library.path");
            log.debug("loadLibrary: {}", javaLibraryPath);
            String jarPath = null;
            try {
                Class<?> self = NDI.class;
                URL location = self.getResource(String.format("/%s.class", self.getName().replace('.', '/')));
                log.debug("Resource path: {}", location.getPath());
                jarPath = location.getPath().split("!")[0];
                if (jarPath.startsWith("file:")) {
                    jarPath = jarPath.substring(jarPath.indexOf(':') + 1);
                }
                // strip any '/' prefix in windows paths which would crash path normalization in extraction
                if (osDescriptor.contains("Windows") && jarPath.charAt(0) == '/') {
                    jarPath = jarPath.substring(1);
                }
                log.debug("Jar path: {}", jarPath);
                // grab the library name from the jar path for possible use further along
                libraryName = jarPath.substring(jarPath.lastIndexOf('/') + 1, jarPath.lastIndexOf(".jar"));
                log.debug("Library name: {}", libraryName);
            } catch (Exception e) {
                log.warn("Exception determining jar name/path", e);
            }
            // extract the shared libs
            try {
                // if the force load flag is set, we will also System.load the libs
                extractShared(jarPath);
            } catch (Exception e) {
                log.warn("Exception extracting shared libraries", e);
            }
            // full path to the main lib
            String ndiPath = null;
            // System.load if flag is set
            if (forceLoad && forcedLoadPaths != null) {
                log.debug("Libraries to load: {}", forcedLoadPaths);
                for (String filePath : forcedLoadPaths) {
                    try {
                        Path absPath = Paths.get(filePath).normalize();
                        File file = absPath.toFile();
                        if (!Files.isSymbolicLink(absPath) && file.length() > 64) {
                            // skip loading lib here to prevent issues with dep libs
                            if (filePath.contains("red5-ndi-")) {
                                log.debug("Skipping ndi: {}", filePath);
                                ndiPath = filePath;
                            } else {
                                log.debug("Load path: {}", filePath);
                                try {
                                    if (file.exists()) {
                                        log.debug("Can read: {} execute: {}", file.canRead(), file.canExecute());
                                        System.load(file.getAbsolutePath());
                                        log.info("Library loaded: {}", filePath);
                                    } else {
                                        throw new Exception("File not found: " + filePath);
                                    }
                                } catch (Throwable e1) {
                                    // we only really care about this when we're debugging, otherwise it may just look like noise in the logs
                                    if (log.isDebugEnabled()) {
                                        // skip all the `file too short` exceptions, just annoying
                                        if (e1 instanceof UnsatisfiedLinkError && !e1.getMessage().contains("too short")) {
                                            log.warn("Link error on load", e1);
                                        }
                                    }
                                    // the next lines will attempt to load a native lib that was not absolutely resolved from the "system"
                                    int slashPos = filePath.lastIndexOf('/');
                                    String libName = filePath.substring(slashPos + 1, filePath.indexOf('.', slashPos));
                                    log.debug("Library name: {} normalized: {}", libName, libName.substring(3));
                                    try {
                                        if (libName.startsWith("lib")) {
                                            System.loadLibrary(libName.substring(3));
                                        } else {
                                            System.loadLibrary(libName);
                                        }
                                        log.info("Library loaded: {}", filePath);
                                    } catch (Throwable e2) {
                                        if (log.isDebugEnabled()) {
                                            // skip all the `file too short` exceptions, just annoying
                                            if (e2 instanceof UnsatisfiedLinkError && !e2.getMessage().contains("too short")) {
                                                log.warn("Link error on loadLibrary: {}", libName, e2);
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            log.debug("Skipping non-binary: {}", filePath);
                        }
                    } catch (Exception e) {
                        log.warn("Exception loading: {}", filePath);
                    }
                }
            }
            // try if the library is on the configured library path
            if (ndiPath != null) {
                log.debug("Load library: {}", ndiPath);
                try {
                    System.load(ndiPath);
                    log.info("Red5 NDI library loaded");
                } catch (Exception e) {
                    log.warn("Exception loading: {}", ndiPath, e);
                    return;
                }
            } else {
                log.warn("NDI path was null, attempting loadLibrary instead");
                try {
                    System.loadLibrary("ndi");
                    log.info("NDI library loaded");
                    System.loadLibrary(libraryName);
                    log.info("Red5 NDI library loaded");
                } catch (Exception e) {
                    log.warn("Exception loading", e);
                    return;
                }
            }
            // set to true if we get this far
            loaded.compareAndSet(false, true);
        }
    }

    private static String getAOL() {
        // choose the list of known AOLs for the current platform
        if (ao.contains("Windows")) { // catches any MS Windows (2nd most likely OS)
            // hand back simple windows aol, nevermind any of their versioning
            return "amd64-Windows-msvc";
        }
        // catches all other OS'es and fall-throughs
        return String.format("%s-gpp", ao);
    }

    /**
     * Extracts / deploys the shared libraries to the native library directory.
     *
     * @param jarPath
     * @throws Exception
     */
    private static void extractShared(final String jarPath) throws Exception {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(Files.newInputStream(Paths.get(jarPath)));
            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                log.debug("Entry: {}", entryName);
                String[] parts = entryName.split("/");
                log.debug("Parts: {}", Arrays.toString(parts));
                // ensure we're in the lib directory in the jar
                if ("lib".equals(parts[0])) {
                    if (parts.length == 4) {
                        // check for our aol
                        String aol = parts[1];
                        if (osDescriptor.indexOf(aol) != -1) {
                            log.debug("Extracting: {}", aol);
                            // ensure the destination directory exists and if not create it
                            Path jniDirectoryPath = Paths.get("lib", "ndi");
                            if (!Files.exists(jniDirectoryPath)) {
                                log.debug("Creating directory for native libs: {}", jniDirectoryPath);
                                Files.createDirectories(jniDirectoryPath);
                            }
                            // check the size since we dont want broken links (linux only issue)
                            if (!entry.isDirectory()) {
                                String libraryFileName = parts[parts.length - 1];
                                String filePath = String.format("%s%s%s", jniDirectoryPath.toAbsolutePath(), File.separatorChar, libraryFileName);
                                log.debug("Extract {} to {}", libraryFileName, filePath);
                                extractFile(zis, filePath);
                                zis.closeEntry();
                                if (forceLoad) {
                                    log.info("Storing library path for forced loading: {}", filePath);
                                    if (forcedLoadPaths == null) {
                                        forcedLoadPaths = new ArrayList<>();
                                    }
                                    forcedLoadPaths.add(filePath);
                                }
                            } else {
                                log.debug("Skipping directory");
                            }
                        }
                    }
                }
            }
        } finally {
            if (zis != null) {
                zis.close();
            }
        }
    }

    /**
     * Extracts a zip entry (file entry).
     * 
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    public static void setForceLoad(boolean forceLoad) {
        NDI.forceLoad = forceLoad;
    }

    public static void setForcedLoadPaths(List<String> forcedLoadPaths) {
        NDI.forcedLoadPaths = forcedLoadPaths;
    }

    /* testing
    public static void main(String[] args) throws InterruptedException {
        log.info("NDI");
        // set debugging flag
        debug = true;
        if (args != null && args.length > 0) {
            // get the party started
            NDI.loadLibrary();
            // create a configuration
            NDIConfig config = new NDIConfig();
            config.name = args.length == 0 ? "Red5 Test Source" : args[0];
            NDISender sender = NDISender.build(config);
            log.info("Sender id: {}", sender.getId());
            NDIReceiver receiver = sender.getReceiver();
            // get the receiver thread
            Thread recv = new Thread(() -> {
                do {
                    NDIPacket pkt = receiver.getNext();
                    if (pkt != null) {
                        log.info("Sender received: {}", pkt);
                    } else {
                        try {
                            Thread.sleep(10L);
                        } catch (Exception e) {
                            // no op
                        }
                    }
                } while (debug);
            }, "ReceiveHandler");
            recv.setDaemon(true);
            recv.start();
            // start the sender
            Thread qs = new Thread(() -> {
                try {
                    sender.start();
                } catch (Throwable t) {
                    log.warn("Exception in start", t);
                }
            }, "StartHandler");
            qs.setDaemon(true);
            qs.start();
            // wait a few ticks
            Thread.sleep(3L * 60000L);
            // stop
            sender.stop();
            debug = false;
            recv.join(1000L);
            log.info("Finished");
        } else {
            System.out.println("Usage: NDI [name]");
        }
    }
    */
}

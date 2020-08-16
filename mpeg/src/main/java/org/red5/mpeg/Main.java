package org.red5.mpeg;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
 * MPEG1, MP2, and MPEG-TS library loader.
 *
 * @author Paul Gregoire
 */
public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

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
        log.info("TS Main on {}", osDescriptor);
        if (!loaded.get()) {
            String javaLibraryPath = System.getProperty("java.library.path");
            log.debug("loadLibrary: {}", javaLibraryPath);
            String jarPath = null;
            try {
                Class<?> self = Main.class;
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
            String mpegPath = null;
            // System.load if flag is set
            if (forceLoad && forcedLoadPaths != null) {
                log.debug("Libraries to load: {}", forcedLoadPaths);
                for (String filePath : forcedLoadPaths) {
                    try {
                        Path absPath = Paths.get(filePath).normalize();
                        File file = absPath.toFile();
                        if (!Files.isSymbolicLink(absPath) && file.length() > 64) {
                            // skip loading lib here to prevent issues with dep libs
                            if (filePath.contains("red5-mpeg-")) {
                                log.debug("Skipping mpeg: {}", filePath);
                                mpegPath = filePath;
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
            if (mpegPath != null) {
                log.debug("Load library: {}", mpegPath);
                try {
                    System.load(mpegPath);
                    log.info("Red5 MPEG library loaded");
                } catch (Exception e) {
                    log.warn("Exception loading: {}", mpegPath, e);
                    return;
                }
            } else {
                log.warn("MPEG path was null, attempting loadLibrary instead");
                try {
                    System.loadLibrary("red5-mpeg-1.0.0-SNAPSHOT");
                    log.info("MPEG library loaded");
                    System.loadLibrary(libraryName);
                    log.info("Red5 MPEG library loaded");
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
                    if (parts.length == 3) {
                        // check for our aol
                        String aol = parts[1];
                        if (osDescriptor.indexOf(aol) != -1) {
                            log.debug("Extracting: {}", aol);
                            // ensure the destination directory exists and if not create it
                            Path jniDirectoryPath = Paths.get("lib", "mpeg");
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
        Main.forceLoad = forceLoad;
    }

    public static void setForcedLoadPaths(List<String> forcedLoadPaths) {
        Main.forcedLoadPaths = forcedLoadPaths;
    }

    public static void main(String[] args) throws Exception {
        log.info("MPEG Main");
        // set debugging flag
        debug = true;
        if (args != null && args.length > 0) {
            boolean testBytes = "testBytes".equals(args[1]);
            Path testFile = null;
            if (!testBytes) {
                // then we expect a file name
                testFile = Paths.get(args[1]);
            }
            // get the party started
            Main.loadLibrary();
            // create a configuration
            TSConfig config = new TSConfig();
            config.name = args.length == 0 ? "Red5 Mpeg" : args[0];
            config.pmtPid = (short) 4096;
            config.videoPid = (short) 256;
            TSHandler handler = TSHandler.build(config);
            log.info("Handler id: {}", handler.getId());
            TSReceiver receiver = handler.getReceiver();
            // get the receiver thread
            Thread recv = new Thread(() -> {
                do {
                    TSPacket pkt = receiver.getNext();
                    if (pkt != null) {
                        log.info("Received: {}", pkt);
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
            if (testBytes) {
                // ts test data
                ByteBuffer tsDataBuffer = ByteBuffer.wrap(Main.intArrayToByteArray(71, 64, 17, 16, 0, 66, -16, 37, 0, 1, -63, 0, 0, -1, 1, -1, 0, 1, -4, -128, 20, 72, 18, 1, 6, 70, 70, 109, 112, 101, 103, 9, 83, 101, 114, 118, 105, 99, 101, 48, 49, 119, 124, 67, -54, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 71, 64, 0, 16, 0, 0, -80, 13, 0, 1, -63, 0, 0, 0, 1, -16, 0, 42, -79, 4, -78, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 71, 80, 0, 16, 0, 2, -80, 18, 0, 1, -63, 0, 0, -31, 0,
                -16, 0, 27, -31, 0, -16, 0, 21, -67, 77, 86, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 71, 65, 0, 48, 7, 80, 0, 0, 123, 12, 126, 0, 0, 0, 1, -32, 0, 0, -128, -64, 10, 49, 0, 9, 18, -7, 17, 0, 7, -40, 97,
                0, 0, 0, 1, 9, -16, 0, 0, 0, 1, 103, 122, 0, 30, -68, -39, 64, -96, 61, -95, 0, 0, 3, 0, 1, 0, 0, 3, 0, 48, 15, 22, 45, -106, 0, 0, 0, 1, 104, -21, -29, -53, 34, -64, 0, 0, 1, 6, 5, -1, -1, -87, -36, 69, -23, -67, -26, -39, 72, -73, -106, 44, -40, 32, -39, 35, -18, -17, 120, 50, 54, 52, 32, 45, 32, 99, 111, 114, 101, 32, 49, 53, 50, 32, 114, 50, 56, 53, 52, 32, 101, 57, 97, 53, 57, 48, 51, 32, 45, 32,
                72, 46, 50, 54, 52, 47, 77, 80, 69, 71, 45, 52, 32, 65, 86, 67, 32, 99, 111, 100, 101, 99, 32, 45, 32, 67, 111, 112, 121, 108, 101, 102, 116, 32, 50, 48, 48, 51, 45, 50, 48, 49, 55, 32, 45, 32, 104, 116, 116, 112, 58, 47, 47, 119, 119, 119, 46, 71, 1, 0, 17, 118, 105, 100, 101, 111, 108, 97, 110, 46, 111, 114, 103, 47, 120, 50, 54, 52, 46, 104, 116, 109, 108, 32, 45, 32, 111, 112, 116, 105, 111, 110,
                115, 58, 32, 99, 97, 98, 97, 99, 61, 49, 32, 114, 101, 102, 61, 51, 32, 100, 101, 98, 108, 111, 99, 107, 61, 49, 58, 48, 58, 48, 32, 97, 110, 97, 108, 121, 115, 101, 61, 48, 120, 51, 58, 48, 120, 49, 49, 51, 32, 109, 101, 61, 104, 101, 120, 32, 115, 117, 98, 109, 101, 61, 55, 32, 112, 115, 121, 61, 49, 32, 112, 115, 121, 95, 114, 100, 61, 49, 46, 48, 48, 58, 48, 46, 48, 48, 32, 109, 105, 120, 101,
                100, 95, 114, 101, 102, 61, 49, 32, 109, 101, 95, 114, 97, 110, 103, 101, 61, 49, 54, 32, 99, 104, 114, 111, 109, 97, 95, 109, 101, 61, 49, 32, 116, 114, 101, 108, 108, 105, 115, 61, 49, 32, 56, 120, 56, 100, 99, 116, 61, 49, 32, 99, 113, 109, 61, 48, 32, 100, 101, 97, 100, 122, 71, 1, 0, 18, 111, 110, 101, 61, 50, 49, 44, 49, 49, 32, 102, 97, 115, 116, 95, 112, 115, 107, 105, 112, 61, 49, 32, 99,
                104, 114, 111, 109, 97, 95, 113, 112, 95, 111, 102, 102, 115, 101, 116, 61, 45, 50, 32, 116, 104, 114, 101, 97, 100, 115, 61, 54, 32, 108, 111, 111, 107, 97, 104, 101, 97, 100, 95, 116, 104, 114, 101, 97, 100, 115, 61, 49, 32, 115, 108, 105, 99, 101, 100, 95, 116, 104, 114, 101, 97, 100, 115, 61, 48, 32, 110, 114, 61, 48, 32, 100, 101, 99, 105, 109, 97, 116, 101, 61, 49, 32, 105, 110, 116, 101, 114,
                108, 97, 99, 101, 100, 61, 48, 32, 98, 108, 117, 114, 97, 121, 95, 99, 111, 109, 112, 97, 116, 61, 48, 32, 99, 111, 110, 115, 116, 114, 97, 105, 110, 101, 100, 95, 105, 110, 116, 114, 97, 61, 48, 32, 98, 102, 114, 97, 109, 101, 115, 61, 51, 32, 98, 95, 112, 121, 114, 97, 109, 105, 100, 61, 50, 32, 98, 95, 97, 100, 97, 112, 116, 71, 1, 0, 19, 61, 49, 32, 98, 95, 98, 105, 97, 115, 61, 48, 32, 100, 105,
                114, 101, 99, 116, 61, 49, 32, 119, 101, 105, 103, 104, 116, 98, 61, 49, 32, 111, 112, 101, 110, 95, 103, 111, 112, 61, 48, 32, 119, 101, 105, 103, 104, 116, 112, 61, 50, 32, 107, 101, 121, 105, 110, 116, 61, 49, 50, 48, 32, 107, 101, 121, 105, 110, 116, 95, 109, 105, 110, 61, 49, 50, 32, 115, 99, 101, 110, 101, 99, 117, 116, 61, 48, 32, 105, 110, 116, 114, 97, 95, 114, 101, 102, 114, 101, 115, 104,
                61, 48, 32, 114, 99, 95, 108, 111, 111, 107, 97, 104, 101, 97, 100, 61, 52, 48, 32, 114, 99, 61, 99, 114, 102, 32, 109, 98, 116, 114, 101, 101, 61, 49, 32, 99, 114, 102, 61, 50, 51, 46, 48, 32, 113, 99, 111, 109, 112, 61, 48, 46, 54, 48, 32, 113, 112, 109, 105, 110, 61, 48, 32, 113, 112, 109, 97, 120, 61, 54, 57, 32, 113, 112, 115, 116, 101, 112, 61, 52, 32, 105, 112, 71, 1, 0, 20, 95, 114, 97, 116,
                105, 111, 61, 49, 46, 52, 48, 32, 97, 113, 61, 49, 58, 49, 46, 48, 48, 0, -128, 0, 0, 1, 101, -120, -124, 0, 87, 104, -12, -84, 23, 78, -5, -42, -14, -4, 28, 69, -74, -96, 73, -32, 6, -37, 39, -113, 48, 119, -24, -60, -37, -51, -56, 28, -37, -100, -76, -68, -39, 75, -68, 47, -4, -64, 126, -38, -107, 23, 8, 88, -15, -67, -109, -93, 90, 38, -37, -108, 24, 112, 101, -43, -34, 121, 82, 61, -52, 24, 45,
                123, 12, 47, -64, 38, 24, -123, -85, -17, 11, 74, -69, -56, 79, 44, 100, -80, 126, 33, 35, 60, -119, -64, 93, 3, 3, 1, -77, -91, 35, 122, -67, -47, -9, 98, -1, -3, 32, 31, -126, 119, 105, -122, 44, 32, -60, -75, -4, -105, 36, -73, 25, 21, -29, -111, -98, -89, -127, -50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
                // 188 byte chunk
                byte[] tsDataChunk = new byte[188];
                while (tsDataBuffer.remaining() >= 188) {
                    // grab a chunk from the bb
                    tsDataBuffer.get(tsDataChunk);
                    log.debug("Got chunk: {} {}", Integer.toHexString(tsDataChunk[0]), tsDataChunk[0]);
                    // demux the chunk
                    handler.demux(tsDataChunk);
                    // sleep for a tick
                    Thread.sleep(100L);
                }
                log.debug("Chunk feeder finished");
            }
            // read a ts file
            if (testFile != null) {
                byte[] tsDataChunk = new byte[188];
                RandomAccessFile file = new RandomAccessFile(testFile.toFile(), "r");
                FileChannel chan = file.getChannel();
                ByteBuffer buffer = ByteBuffer.allocate(188 * 10);
                while (chan.read(buffer) > 0) {
                    buffer.flip();
                    while (buffer.remaining() >= 188) {
                        buffer.get(tsDataChunk);
                        log.debug("Got chunk: {} {}", Integer.toHexString(tsDataChunk[0]), tsDataChunk[0]);
                        // demux the chunk
                        handler.demux(tsDataChunk);
                        // sleep for a tick
                        Thread.sleep(100L);
                    }
                    buffer.clear(); // do something with the data and clear/compact it.
                }
                chan.close();
                file.close();
            }
            // wait a few ticks
            Thread.sleep(7000L);
            debug = false;
            recv.join(100L);
            log.info("Finished");
        } else {
            System.out.println("Usage: Main [name]");
        }
    }

    public final static byte[] intArrayToByteArray(int... ints) {
        byte[] data = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            data[i] = (byte) ((ints[i] & 0x000000FF) >> 0);
        }
        return data;
    }
}

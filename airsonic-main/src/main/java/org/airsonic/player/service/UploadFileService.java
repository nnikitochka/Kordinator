package org.airsonic.player.service;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import org.airsonic.player.domain.User;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.LambdaUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** File storage and archive extraction shared by the Ktor upload endpoint. */
@Service
public class UploadFileService {
    private static final Logger LOG = LoggerFactory.getLogger(UploadFileService.class);
    private static final Set<String> ARCHIVES = Set.of("zip", "7z", "rar", "cpio", "jar", "tar");
    private final SecurityService security;
    public UploadFileService(SecurityService security) { this.security = security; }

    public void checkAllowed(User user, Path path, boolean checkExistence) throws IOException {
        if (!user.isAdminRole() && !user.isUploadRole()) throw new AccessDeniedException("Upload role required");
        Path normalized = path.toAbsolutePath().normalize();
        for (Path parent = normalized; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new AccessDeniedException("Symbolic link in upload path");
        }
        try {
            security.checkUploadAllowed(normalized, checkExistence);
        } catch (AccessDeniedException e) {
            if (!user.isAdminRole() || !SecurityService.isFileInFolder(normalized, SettingsService.getAirsonicHome())) throw e;
        }
    }

    public void store(User user, Path temporary, String filename, Path directory, boolean extract,
            List<Path> uploaded, List<Path> unzipped, List<Exception> errors) throws IOException {
        Path target = directory.resolve(filename).normalize();
        checkAllowed(user, target, true);
        // Only the existing admin exception inside airsonic.home permits replacing files.
        if (user.isAdminRole() && SecurityService.isFileInFolder(target, SettingsService.getAirsonicHome())) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(temporary, target);
        }
        uploaded.add(target);
        LOG.info("Uploaded {}", target);
        String extension = com.google.common.io.MoreFiles.getFileExtension(target).toLowerCase(Locale.ROOT);
        if (extract && ARCHIVES.contains(extension)) unzip(user, target, unzipped, errors);
    }

    private void unzip(User user, Path file, List<Path> unzippedFiles, List<Exception> exceptions) {
        LOG.info("Unzipping {}", file);
        boolean unzipped = false;

        // rar files
        if (file.getFileName().toString().toLowerCase().endsWith(".rar")) {
            LOG.info("Trying rar-specific extraction method for {}", file);
            try (Archive zip = new Archive(file.toFile())) {
                if (zip.isEncrypted()) {
                    throw new AccessDeniedException(file.toString(), null, "Archive is encrypted");
                }

                for (FileHeader fh : zip) {
                    if (fh.isEncrypted()) {
                        LOG.info("Can't read {} in {}", fh.getFileName(), file);
                        continue;
                    }

                    copyEntry(user, file, new ArchiveEntry() {
                        @Override
                        public boolean isDirectory() {
                            return fh.isDirectory();
                        }

                        @Override
                        public long getSize() {
                            return fh.getFullUnpackSize();
                        }

                        @Override
                        public String getName() {
                            return fh.getFileName();
                        }

                        @Override
                        public Date getLastModifiedDate() {
                            return fh.getArcTime();
                        }
                    }, dest -> {
                            try (OutputStream os = Files.newOutputStream(dest)) {
                                zip.extractFile(fh, os);
                            } catch (RarException e) {
                                throw new IOException(e);
                            }
                        }, unzippedFiles, exceptions);
                }
            } catch (Exception e) {
                LOG.warn("Something went wrong unzipping {}", file, e);
                exceptions.add(e);
            } finally {
                unzipped = true;
                FileUtil.delete(file);
            }
        }

        // zip files
        if (file.getFileName().toString().toLowerCase().endsWith(".zip")) {
            LOG.info("Trying zip-specific extraction method for {}", file);
            try (FileChannel channel = FileChannel.open(file); ZipFile zip = new ZipFile(channel)) {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                ZipArchiveEntry entry;
                while (entries.hasMoreElements()) {
                    entry = entries.nextElement();

                    if (!zip.canReadEntryData(entry)) {
                        LOG.info("Can't read {} in {}", entry.getName(), file);
                        continue;
                    }

                    try (InputStream is = zip.getInputStream(entry)) {
                        copyEntry(user, file, entry, dest -> Files.copy(is, dest), unzippedFiles, exceptions);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Something went wrong unzipping {}", file, e);
                exceptions.add(e);
            } finally {
                unzipped = true;
                FileUtil.delete(file);
            }
        }

        // 7z files
        if (file.getFileName().toString().toLowerCase().endsWith(".7z")) {
            LOG.info("Trying 7z-specific extraction method for {}", file);
            try (FileChannel channel = FileChannel.open(file); SevenZFile zip = new SevenZFile(channel)) {
                byte[] buffer = new byte[8042];
                SevenZArchiveEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    copyEntry(user, file, entry, dest -> {
                        try (OutputStream os = Files.newOutputStream(dest)) {
                            int bytesRead;
                            while ((bytesRead = zip.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                        }
                    }, unzippedFiles, exceptions);
                }
            } catch (Exception e) {
                LOG.warn("Something went wrong unzipping {}", file, e);
                exceptions.add(e);
            } finally {
                unzipped = true;
                FileUtil.delete(file);
            }
        }

        // Generic
        if (!unzipped) {
            LOG.info("Trying a generic extraction method for {}", file);
            try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file));
                    ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(bis)) {

                ArchiveEntry entry;
                while ((entry = ais.getNextEntry()) != null) {
                    if (!ais.canReadEntryData(entry)) {
                        LOG.info("Can't read {} in {}", entry.getName(), file);
                        continue;
                    }
                    copyEntry(user, file, entry, dest -> Files.copy(ais, dest), unzippedFiles, exceptions);
                }
            } catch (Exception e) {
                LOG.warn("Something went wrong unzipping {}", file, e);
                exceptions.add(e);
            } finally {
                FileUtil.delete(file);
            }
        }
    }

    private void copyEntry(User user, Path file, ArchiveEntry entry, LambdaUtils.ThrowingConsumer<Path, IOException> copier,
            List<Path> unzippedFiles, List<Exception> exceptions) {
        final Path toPath = file.resolveSibling(entry.getName().replace('\\', '/')).normalize();
        try {
            if (!toPath.normalize().startsWith(file.getParent())) {
                throw new IOException("Bad zip filename: " + toPath);
            }
            checkAllowed(user, toPath, !entry.isDirectory());
            if (entry.isDirectory()) {
                Files.createDirectories(toPath);
            } else {
                Path parent = toPath.getParent();
                Files.createDirectories(parent);
                if (!Files.isDirectory(parent)) {
                    throw new IOException("Failed to create directory: " + parent);
                }

                copier.accept(toPath);
                unzippedFiles.add(toPath);
                LOG.debug("Unzipped {}", toPath);

            }
        } catch (IOException e) {
            exceptions.add(e);
            LOG.debug("Could not unzip {}", toPath, e);
        }

        LOG.info("Processed {}", toPath);
    }

}

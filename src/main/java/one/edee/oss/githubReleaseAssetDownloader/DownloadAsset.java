package one.edee.oss.githubReleaseAssetDownloader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and extracts asset from GitHub release. Currently, only zip archives are supported.
 *
 * @author Lukáš Hornych 2023
 */
@Mojo(name = "download-asset")
public class DownloadAsset extends AbstractMojo {

    private static final String GITHUB_RELEASE_API_URL_TEMPLATE = "https://api.github.com/repos/%s/%s/releases/latest";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();

    @Parameter(property = "owner", required = true)
    private String owner;

    @Parameter(property = "repo", required = true)
    private String repo;

    @Parameter(property = "assetName", required = true)
    private String assetName;

    @Parameter(property = "targetDir", required = true)
    private File targetDir;

    public void execute() throws MojoExecutionException {
        cleanTargetDir();

        final Map<String, Object> release = fetchRelease();
        final Map<String, Object> asset = findAsset(release);
        downloadAsset(asset);

        getLog().info("Asset `" + assetName + "` downloaded and extracted to `" + targetDir.getAbsolutePath() + "`.");
    }

    private void cleanTargetDir() throws MojoExecutionException {
        getLog().info("Cleaning target directory `" + targetDir.getAbsolutePath() + "`...");
        for (File file : targetDir.listFiles()) {
            // delete only nested files, not the target directory itself
            try {
                Files.walk(file.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                throw new MojoExecutionException("Could not delete target director `" + targetDir.getAbsolutePath() + "`.", e);
            }
        }
        if (targetDir.listFiles().length > 0) {
            throw new MojoExecutionException("Target directory still contains files even after cleaning.");
        }
        getLog().info("Target directory cleaned.");
    }

    @Nonnull
    private Map<String, Object> fetchRelease() throws MojoExecutionException {
        getLog().info("Fetching GitHub release for `" + owner + "/" + repo + "`...");

        final String releaseApiUrl = String.format(GITHUB_RELEASE_API_URL_TEMPLATE, owner, repo);
        final HttpRequest releaseRequest = HttpRequest.newBuilder(URI.create(releaseApiUrl))
            .GET()
            .build();

        final HttpResponse<String> releaseResponse;
        try {
            releaseResponse = httpClient.send(releaseRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Could not download GitHub release: ", e);
        }

        try {
            //noinspection unchecked
            final Map<String, Object> release = (Map<String, Object>) objectMapper.readValue(releaseResponse.body(), Map.class);
            getLog().info("GitHub release fetched.");
            return release;
        } catch (JsonProcessingException e) {
            throw new MojoExecutionException("Could not parse GitHub release: ", e);
        }
    }

    @Nonnull
    private Map<String, Object> findAsset(@Nonnull Map<String, Object> release) throws MojoExecutionException {
        //noinspection unchecked
        final List<Map<String, Object>> assets = (List<Map<String, Object>>) release.get("assets");
        if (assets == null || assets.isEmpty()) {
            throw new MojoExecutionException("No assets found in GitHub release.");
        }

        final Map<String, Object> asset = assets.stream()
            .filter(it -> assetName.equals(it.get("name")))
            .findFirst()
            .orElseThrow(() -> new MojoExecutionException("Asset not found in GitHub release."));
        final String assetContentType = (String) asset.get("content_type");
        if (!"application/zip".equals(assetContentType)) {
            throw new MojoExecutionException("Only zip archives are supported.");
        }

        return asset;
    }

    private void downloadAsset(@Nonnull Map<String, Object> asset) throws MojoExecutionException {
        getLog().info("Downloading asset `" + assetName + "`...");

        final String assetDownloadUrl = (String) asset.get("browser_download_url");
        final HttpRequest assetRequest = HttpRequest.newBuilder(URI.create(assetDownloadUrl))
            .GET()
            .build();

        final HttpResponse<InputStream> assetResponse;
        try {
            assetResponse = httpClient.send(assetRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Could not download asset: ", e);
        }

        getLog().info("Asset downloaded, extracting...");

        try (final InputStream assetStream = assetResponse.body();
             final ZipInputStream assetArchive = new ZipInputStream(assetStream)) {
            ZipEntry entry = assetArchive.getNextEntry();
            while (entry != null) {
                final String filePath = targetDir + File.separator + entry.getName();
                if (entry.isDirectory()) {
                    Files.createDirectory(Path.of(filePath));
                } else {
                    extractFile(assetArchive, filePath);
                }
                assetArchive.closeEntry();
                entry = assetArchive.getNextEntry();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not extract asset: ", e);
        }

        getLog().info("Asset extracted to `" + targetDir.getAbsolutePath() + "`.");
    }

    private void extractFile(@Nonnull ZipInputStream zipIn, @Nonnull String targetFilePath) throws IOException {
        try (final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFilePath))) {
            final byte[] bytesIn = new byte[4096];
            int read = 0;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }
}

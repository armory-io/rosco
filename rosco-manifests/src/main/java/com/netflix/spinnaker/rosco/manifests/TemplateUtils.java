package com.netflix.spinnaker.rosco.manifests;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
@Slf4j
public abstract class TemplateUtils {
  @Autowired
  ClouddriverService clouddriverService;

  private static final String PATH_SEPARATOR = "contents/";

  private RetrySupport retrySupport = new RetrySupport();

  private String nameFromReference(String reference) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return DatatypeConverter.printHexBinary(md.digest(reference.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to save bake manifest: " + e.getMessage(), e);
    }
  }

  protected Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact) throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    Path path = Paths.get(env.getStagingPath().toString(), nameFromReference(artifact.getReference()));
    OutputStream outputStream = new FileOutputStream(path.toString());

    Response response = retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);

    if (response.getBody() != null) {
      InputStream inputStream = response.getBody().in();
      IOUtils.copy(inputStream, outputStream);
      inputStream.close();
    }
    outputStream.close();

    return path;
  }

  protected void downloadArtifactToTmpFileStructure(BakeManifestEnvironment env, Artifact artifact) throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    Path githubPath =  Paths.get(artifact.getReference().substring(
            artifact.getReference().indexOf(PATH_SEPARATOR)+PATH_SEPARATOR.length()));
    String filename = githubPath.getFileName().toString();
    String subfolder = githubPath.toString().replace(filename,"");

    Path tmpPath = Paths.get(env.getStagingPath().resolve(subfolder).toString());
    Files.createDirectories(tmpPath);
    File newfile = new File(env.getStagingPath().resolve(subfolder).resolve(filename).toString());
    newfile.createNewFile();
    OutputStream outputStream = new FileOutputStream(newfile);

    Response response = retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);

    if (response.getBody() != null) {
      InputStream inputStream = response.getBody().in();
      IOUtils.copy(inputStream, outputStream);
      inputStream.close();
    }
    outputStream.close();
  }

  public String getKustomizationPath(BakeManifestEnvironment env, Artifact artifact){
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }

    Path path =  Paths.get(
            FilenameUtils.getPath(artifact.getReference()).substring(
                    FilenameUtils.getPath(artifact.getReference()).indexOf(PATH_SEPARATOR)+PATH_SEPARATOR.length()));
    return env.getStagingPath().resolve(path).toString();
  }

  public static class BakeManifestEnvironment {
    @Getter
    final private Path stagingPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

    public BakeManifestEnvironment() {
      boolean success = stagingPath.toFile().mkdirs();
      if (!success) {
        log.warn("Failed to make directory " + stagingPath + "...");
      }
    }

    public void cleanup() {
      try {
        FileUtils.deleteDirectory(stagingPath.toFile());
      } catch (IOException e) {
        throw new RuntimeException("Failed to cleanup bake manifest environment: " + e.getMessage(), e);
      }
    }
  }
}

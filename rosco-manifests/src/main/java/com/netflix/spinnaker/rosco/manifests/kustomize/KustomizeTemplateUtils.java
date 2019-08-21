package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KustomizeTemplateUtils extends TemplateUtils {

  private HashSet<String> filesToDownload = new HashSet<String>();
  private static final String KUSTOMIZATION_FILE = "kustomization";

  @Autowired
  private KustomizationFileReader kustomizationFileReader;


  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, KustomizeBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());
    filesToDownload = new HashSet<>();

    List<Path> valuePaths = new ArrayList<>();
    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }
    List<Artifact> artifacts = new ArrayList<>();
    Artifact artifact = inputArtifacts.get(0);
    String templatePath = getKustomizationPath(env,artifact);
    try {
      Path path = Paths.get(artifact.getReference());
        artifact.setReference(path.toString());
      for (String s : getFilesFromGithub(artifact)) {
        Artifact a = new Artifact();
        a.setReference(s);
        a.setArtifactAccount(artifact.getArtifactAccount());
        a.setCustomKind(artifact.isCustomKind());
        a.setLocation(artifact.getLocation());
        a.setMetadata(artifact.getMetadata());
        a.setName(artifact.getName());
        a.setProvenance(artifact.getProvenance());
        a.setType(artifact.getType());
        a.setUuid(artifact.getUuid());
        a.setVersion(artifact.getVersion());
        artifacts.add(a);
      }
    } catch (IOException e) {
      log.error("Error setting references in artifacts from GitHub " + e.getMessage());
    }
    try {
      for(Artifact ar : artifacts){
        downloadArtifactToTmpFileStructure(env, ar);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch helm template: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("kustomize");
    command.add("build");
    command.add( templatePath);


      result.setCommand(command);

    return result;
  }

  private HashSet<String> getFilesFromGithub(Artifact artifact) throws IOException {
    HashSet<String> toEvaluate = new HashSet<>();
    Path pathe = Paths.get(artifact.getReference());
    Kustomization kustomization = kustomizationFileReader.getKustomization(artifact);
    filesToDownload.add(kustomization.getReference());
    if (kustomization.getResources() != null)
      kustomization.getResources().forEach(f -> toEvaluate.add(f));
    if (kustomization.getConfigMapGenerator() != null) {
      kustomization
              .getConfigMapGenerator()
              .forEach(
                      conf -> {
                        conf.getFiles()
                                .forEach(
                                        f -> {
                                          filesToDownload.add(pathe.resolve(f).toString());
                                        });
                      });
    }
    if (kustomization.getCdrs() != null)
      kustomization.getCdrs().forEach(cdr -> toEvaluate.add(cdr));
    if (kustomization.getGenerators() != null)
      kustomization.getGenerators().forEach(gen -> toEvaluate.add(gen));
    if (kustomization.getPatches() != null)
      kustomization.getPatches().forEach(p -> toEvaluate.add(p.getPath()));
    if (kustomization.getPatchesStrategicMerge() != null)
      kustomization.getPatchesStrategicMerge().forEach(patch -> toEvaluate.add(patch));
    if (kustomization.getPatchesJson6902() != null)
      kustomization.getPatchesJson6902().forEach(json -> toEvaluate.add(json.getPath()));
    if (toEvaluate != null) {
      for (String s : toEvaluate) {
        if (s.contains(".")) {
          String tmp = s.substring(s.lastIndexOf(".") + 1);
          if (!tmp.contains("/")) {
            filesToDownload.add(pathe.resolve(s).toString());
          } else {
            artifact.setReference(getSubFolder(s, pathe));
            getFilesFromGithub(artifact);
          }
        } else {
          artifact.setReference(getSubFolder(s, pathe));
          getFilesFromGithub(artifact);
        }
      }
    }
    return filesToDownload;
  }

  private String getSubFolder(String pRelativePath, Path pPath) {
    String pBasePath = pPath.toString();
    String levels = pRelativePath.substring(0, pRelativePath.lastIndexOf("/") + 1);
    String sPath = pRelativePath.substring(pRelativePath.lastIndexOf("/") + 1);
    if (levels.startsWith("./")) {
      return pBasePath + File.separator + sPath;
    } else {
      int lev = levels.length() - levels.replaceAll("/", "").length();
      for (int i = 0; i < lev; i++) {
        pBasePath = pBasePath.substring(0, pBasePath.lastIndexOf("/"));
      }
      return pBasePath + File.separator + sPath;
    }
  }



}

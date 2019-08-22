/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;


@Component
@Slf4j
public class KustomizeTemplateUtils extends TemplateUtils {

  private HashSet<String> filesToDownload = new HashSet<String>();

  @Autowired
  private KustomizationFileReader kustomizationFileReader;

  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, KustomizeBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());
    filesToDownload = new HashSet<>();
    Artifact artifact = request.getInputArtifact();
    if (artifact == null ) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }
    String kustomizationFileName = FilenameUtils.getName(artifact.getReference());
    if(kustomizationFileName!=null && !kustomizationFileName.toUpperCase().contains("KUSTOMIZATION")){
      throw new IllegalArgumentException("The inputArtifact should be a kustomization file valid.");
    }

    List<Artifact> artifacts = new ArrayList<>();
    String templatePath = getKustomizationPath(env,artifact);
    try {
      Path path = Paths.get(FilenameUtils.getPath(artifact.getReference()));
        artifact.setReference(path.toString());
      HashSet<String> files = getFilesFromGithub(artifact);
      artifacts = files.stream()
              .map(f -> {
                 return Artifact.builder()
                        .reference(f)
                        .artifactAccount(artifact.getArtifactAccount())
                        .customKind(artifact.isCustomKind())
                        .location(artifact.getLocation())
                        .metadata(artifact.getMetadata())
                        .name(artifact.getName())
                        .provenance(artifact.getProvenance())
                        .type(artifact.getType())
                        .version(artifact.getVersion())
                         .build();
              })
              .collect(Collectors.toList());
    } catch (IOException e) {
      log.error("Error setting references in artifacts from GitHub " + e.getMessage());
    }
    try {
      for(Artifact ar : artifacts){
        downloadArtifactToTmpFileStructure(env, ar);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch kustomize files: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
      command.add("kustomize");
      command.add("build");
      command.add( templatePath);
    result.setCommand(command);

    return result;
  }

  private HashSet<String> getFilesFromGithub(Artifact artifact) throws IOException {
    Path githubPath = Paths.get(artifact.getReference());
    Kustomization kustomization = kustomizationFileReader.getKustomization(artifact);
    filesToDownload.add(kustomization.getReference());
    filesToDownload.addAll(kustomization.getFilesToDownload(githubPath));
    if (kustomization.getFilesToEvaluate() != null && !kustomization.getFilesToEvaluate().isEmpty()) {
      for (String evaluate : kustomization.getFilesToEvaluate()) {
        if (evaluate.contains(".")) {
          String tmp = evaluate.substring(evaluate.lastIndexOf(".") + 1);
          if (!tmp.contains("/")) {
            filesToDownload.add(githubPath.resolve(evaluate).toString());
          } else {
            artifact.setReference(getSubFolder(evaluate, githubPath));
            getFilesFromGithub(artifact);
          }
        } else {
          artifact.setReference(getSubFolder(evaluate, githubPath));
          getFilesFromGithub(artifact);
        }
      }
    }
    return filesToDownload;
  }

  private String getSubFolder(String pRelativePath, Path pPath) {
    String basePath = pPath.toString();
    String levels = pRelativePath.substring(0, pRelativePath.lastIndexOf("/") + 1);
    String sPath = pRelativePath.substring(pRelativePath.lastIndexOf("/") + 1);
    if (levels.startsWith("./")) {
      return basePath + File.separator + sPath;
    } else {
      int lev = levels.length() - levels.replaceAll("/", "").length();
      for (int i = 0; i < lev; i++) {
        basePath = basePath.substring(0, basePath.lastIndexOf("/"));
      }
      return basePath + File.separator + sPath;
    }
  }



}

package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KustomizeTemplateUtils extends TemplateUtils {
  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, KustomizeBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    Path templatePath;
    List<Path> valuePaths = new ArrayList<>();
    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }

    try {
      templatePath = downloadArtifactToTmpFile(env, inputArtifacts.get(0));

      // not a stream to keep exception handling cleaner
      for (Artifact valueArtifact : inputArtifacts.subList(1, inputArtifacts.size())) {
        valuePaths.add(downloadArtifactToTmpFile(env, valueArtifact));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch helm template: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("kustomize");
    command.add("build");
    command.add(templatePath.toString());


    result.setCommand(command);

    return result;
  }
}

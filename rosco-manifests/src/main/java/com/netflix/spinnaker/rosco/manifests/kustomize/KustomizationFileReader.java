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
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Component
@Slf4j
public class KustomizationFileReader {
  private final ClouddriverService clouddriverService;

  public KustomizationFileReader(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  private List<String> KUSTOMIZATION_FILENAMES =
      Arrays.asList("kustomization.yaml", "kustomization.yml", "kustomization");
  private RetrySupport retrySupport = new RetrySupport();

  public Kustomization getKustomization(Artifact artifact, String possibleName) throws IOException {
    Path artifactPath = Paths.get(artifact.getReference());
    List<String> names =
        new ArrayList<>(KUSTOMIZATION_FILENAMES)
            .stream()
                .sorted(
                    (a, b) ->
                        a.equals(possibleName) ? -1 : (b.equals(possibleName) ? 1 : a.compareTo(b)))
                .collect(Collectors.toList());

    for (String name : names) {
      try {
        artifact.setReference(artifactPath.resolve(name).toString());
        Kustomization kustomization = convert(artifact);
        kustomization.setKustomizationFilename(name);
        return kustomization;
      } catch (RetrofitError | IOException e) {
        log.error("Unable to convert kustomization file to Object: " + name);
      }
    }

    return null;
  }

  private Stack<String> getKustomizationNames(String possibleName) {
    Stack<String> stackOfNames = new Stack<>();
    KUSTOMIZATION_FILENAMES.stream()
        .filter(s -> !s.equals(possibleName))
        .collect(Collectors.toList())
        .forEach(s -> stackOfNames.push(s));
    stackOfNames.push(possibleName);
    return stackOfNames;
  }

  private Kustomization convert(Artifact artifact) throws IOException {
    return new Yaml().loadAs(downloadFile(artifact), Kustomization.class);
  }

  InputStream downloadFile(Artifact artifact) throws IOException {
    Response response =
        retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
    return response.getBody().in();
  }
}

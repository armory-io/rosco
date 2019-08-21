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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class KustomizationFileReader {

  @Autowired
  ClouddriverService clouddriverService;
  private static final String KUSTOMIZATION_FILE = "kustomization";
  private RetrySupport retrySupport = new RetrySupport();

  public Kustomization getKustomization(Artifact artifact) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    Kustomization kustomization = null;
    Path githubPath = Paths.get(artifact.getReference());
    artifact.setReference(githubPath.resolve(KUSTOMIZATION_FILE + ".yaml").toString());
    try {
      kustomization = mapper.readValue(downloadFile(artifact), Kustomization.class);
    } catch (IOException yamlException) {
      log.error("File does not exist in GitHub " + artifact.getReference());
      artifact.setReference(githubPath.resolve(KUSTOMIZATION_FILE + ".yml").toString());
      try {
        kustomization = mapper.readValue(downloadFile(artifact), Kustomization.class);
      } catch (IOException ymlException) {
        log.error("File does not exist in GitHub " + artifact.getReference());
        artifact.setReference(githubPath.resolve(KUSTOMIZATION_FILE).toString());
        try {
          kustomization = mapper.readValue(downloadFile(artifact), Kustomization.class);
        } catch (IOException e) {
          log.error("File does not exist in GitHub " + artifact.getReference());
          throw new IOException("Unable to convert kustomization file to Object.");
        }
      }
    }
    if(kustomization!=null)
      kustomization.setReference(artifact.getReference());
    return kustomization;
  }

  InputStream downloadFile(Artifact artifact) throws IOException {
    Response response = retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
    return response.getBody().in();
  }
}

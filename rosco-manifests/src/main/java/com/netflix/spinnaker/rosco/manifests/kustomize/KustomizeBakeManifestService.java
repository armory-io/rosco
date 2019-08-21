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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils.BakeManifestEnvironment;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class KustomizeBakeManifestService implements BakeManifestService {
  @Autowired
  KustomizeTemplateUtils kustomizeTemplateUtils;

  @Autowired
  JobExecutor jobExecutor;

  @Override
  public boolean handles(String type) {
    return type.equals("kustomize");
  }

  public Artifact bake(Map<String, Object> request) {
    ObjectMapper mapper = new ObjectMapper();
    KustomizeBakeManifestRequest bakeManifestRequest = mapper.convertValue(request, KustomizeBakeManifestRequest.class);
    BakeManifestEnvironment env = new BakeManifestEnvironment();
    BakeRecipe recipe = kustomizeTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    BakeStatus bakeStatus;
    try {
      JobRequest jobRequest = new JobRequest(
        recipe.getCommand(),
        new ArrayList<>(),
        UUID.randomUUID().toString(),
        AuthenticatedRequest.getSpinnakerExecutionId().orElse(null),
        false
      );
      String jobId = jobExecutor.startJob(jobRequest);
      bakeStatus = jobExecutor.updateJob(jobId);
      while (bakeStatus == null || bakeStatus.getState() == BakeStatus.State.RUNNING) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        bakeStatus = jobExecutor.updateJob(jobId);
      }
      if (bakeStatus.getResult() != BakeStatus.Result.SUCCESS) {
        throw new IllegalStateException("Bake of " + request + " failed: " + bakeStatus.getLogsContent());
      }
    } finally {
      env.cleanup();
    }
    return Artifact.builder()
        .type("embedded/base64")
        .name(bakeManifestRequest.getOutputArtifactName())
        .reference(Base64.getEncoder().encodeToString(bakeStatus.getOutputContent().getBytes()))
        .build();
  }
}
